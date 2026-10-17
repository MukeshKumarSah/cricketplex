package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import com.cricketplex.repository.DefaultLineupRepository;
import com.cricketplex.repository.MatchLineupRepository;
import com.cricketplex.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LineupGenerator {
    private final MatchLineupRepository matchLineupRepository;
    private final DefaultLineupRepository defaultLineupRepository;
    private final PlayerRepository playerRepository;

    public MatchLineup getOrGenerateLineup(Fixture fixture, Team team, String format) {
        Optional<MatchLineup> existing = matchLineupRepository.findByFixtureIdAndTeamId(fixture.getId(), team.getId());
        if (existing.isPresent()) {
            MatchLineup lineup = existing.get();
            // A player may have been sold/fired after this fixture's lineup was saved.
            // Repair it so a departed player can't take the field.
            if (sanitizeLineup(lineup, team)) {
                lineup = matchLineupRepository.save(lineup);
            }
            return lineup;
        }

        Optional<DefaultLineup> savedDefault = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (savedDefault.isEmpty()) {
            // No default yet — build one and persist it as the team's system default
            // so subsequent matches reuse it instead of regenerating every time.
            log.info("No default lineup for team {} ({}) — generating and saving one", team.getTeamName(), format);
            savedDefault = Optional.ofNullable(ensureDefaultLineup(team, format));
        }
        if (savedDefault.isPresent()) {
            MatchLineup fromDefault = buildLineupFromDefault(fixture, team, format, savedDefault.get());
            if (fromDefault != null) return fromDefault;
        }
        throw new IllegalStateException("Team " + team.getTeamName() + " has fewer than 11 players");
    }

    /**
     * Return the team's saved default lineup for a format, creating and persisting
     * a system-generated one on first use. The default is built once from the squad
     * and then reused for every match; players who have since been fired or sold are
     * replaced at build time by {@link #buildLineupFromDefault}.
     *
     * @return the default lineup, or {@code null} if the squad has fewer than 11 players
     */
    public DefaultLineup ensureDefaultLineup(Team team, String format) {
        Optional<DefaultLineup> existing = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (existing.isPresent()) return existing.get();

        Map<String, Object> data = buildSystemDefaultData(team, format);
        if (data == null) return null;
        log.info("Saving system-generated default lineup for team {} ({})", team.getTeamName(), format);
        return defaultLineupRepository.save(DefaultLineup.builder()
                .team(team).format(format).lineupData(data).build());
    }

    /**
     * Build (but don't persist) the system default lineup data map for a team.
     * Uses the same auto-selection heuristics as the legacy on-demand generator,
     * and stores it in the same shape the lineup UI reads and writes.
     */
    public Map<String, Object> buildSystemDefaultData(Team team, String format) {
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) return null;

        List<Player> selected = autoSelectPlaying11(squad);
        List<Player> battingOrder = buildAutoBattingOrder(selected);
        Player keeper = selected.stream().filter(p -> "KEEPER".equals(p.getRole()))
                .max(Comparator.comparingDouble(Player::getKeeperRating))
                .orElse(selected.stream().max(Comparator.comparingDouble(Player::getKeeperRating)).orElse(selected.get(0)));
        Player captain = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(selected.get(0));
        List<Player> topBowlers = pickTopBowlers(selected, format);
        int bowlingPlanOvers = "FC".equalsIgnoreCase(format) ? 100 : MatchFormatRules.getMaxOvers(format);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captainId", captain.getId().toString());
        data.put("keeperId", keeper.getId().toString());
        data.put("bowlingPlan", "BALANCED");
        data.put("tossChoice", null);
        data.put("batOrBowl", null);

        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 0; i < battingOrder.size(); i++) {
            Player p = battingOrder.get(i);
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("playerId", p.getId().toString());
            pm.put("battingPosition", i + 1);
            pm.put("batAggression", p.getBatAggression());
            players.add(pm);
        }
        data.put("players", players);

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int over = 1; over <= bowlingPlanOvers; over++) {
            Player bowler = topBowlers.get((over - 1) % topBowlers.size());
            Map<String, Object> bo = new LinkedHashMap<>();
            bo.put("overNumber", over);
            bo.put("bowlerId", bowler.getId().toString());
            bo.put("aggression", bowler.getBowlAggression());
            orders.add(bo);
        }
        data.put("bowlingOrders", orders);
        return data;
    }

    private List<Player> pickTopBowlers(List<Player> selected, String format) {
        if ("FC".equalsIgnoreCase(format)) {
            List<Player> topBowlers = selected.stream().filter(p -> p.getBowlRating() >= 15)
                    .sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).toList();
            if (topBowlers.size() >= 5) return topBowlers;
        }
        return selected.stream().sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
    }

    @SuppressWarnings("unchecked")
    public MatchLineup buildLineupFromDefault(Fixture fixture, Team team, String format, DefaultLineup defaultLineup) {
        Map<String, Object> data = defaultLineup.getLineupData();
        if (data == null) return null;
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) return null;
        Map<String, Player> squadById = new HashMap<>();
        for (Player p : squad) squadById.put(p.getId().toString(), p);
        List<Map<String, Object>> rawPlayers = (List<Map<String, Object>>) data.get("players");
        if (rawPlayers == null || rawPlayers.isEmpty()) return null;
        rawPlayers.sort(Comparator.comparingInt(p -> ((Number) p.getOrDefault("battingPosition", 999)).intValue()));
        MatchLineup lineup = MatchLineup.builder().fixture(fixture).team(team)
                .bowlingPlan(Objects.toString(data.getOrDefault("bowlingPlan", "BALANCED"), "BALANCED")).build();
        lineup.setBatOrBowl((String) data.get("batOrBowl"));
        lineup.setTossChoice((String) data.get("tossChoice"));
        Map<String, Player> replacementByOriginalId = new HashMap<>();
        Set<UUID> usedPlayerIds = new HashSet<>();
        List<Player> selected = new ArrayList<>();
        int targetSlots = Math.min(11, rawPlayers.size());
        for (int i = 0; i < targetSlots; i++) {
            Map<String, Object> slot = rawPlayers.get(i);
            String playerId = Objects.toString(slot.get("playerId"), null);
            Player chosen = playerId == null ? null : squadById.get(playerId);
            if (chosen == null || usedPlayerIds.contains(chosen.getId())) {
                String requiredRole = resolveRole(playerId);
                chosen = pickReplacementByRole(squad, usedPlayerIds, requiredRole);
                if (chosen == null) chosen = pickBestRemainingPlayer(squad, usedPlayerIds);
                if (chosen == null) break;
                if (playerId != null) replacementByOriginalId.put(playerId, chosen);
            }
            usedPlayerIds.add(chosen.getId()); selected.add(chosen);
            int battingPosition = ((Number) slot.getOrDefault("battingPosition", i + 1)).intValue();
            String batAgg = Objects.toString(slot.getOrDefault("batAggression", chosen.getBatAggression()), "N");
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(chosen)
                    .battingPosition(battingPosition).batAggression(batAgg).build());
        }
        while (lineup.getPlayers().size() < 11) {
            Player next = pickBestRemainingPlayer(squad, usedPlayerIds);
            if (next == null) break;
            usedPlayerIds.add(next.getId()); selected.add(next);
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(next)
                    .battingPosition(lineup.getPlayers().size() + 1).batAggression(next.getBatAggression()).build());
        }
        lineup.getPlayers().sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        for (int i = 0; i < lineup.getPlayers().size(); i++) lineup.getPlayers().get(i).setBattingPosition(i + 1);
        Map<UUID, Player> selectedById = new HashMap<>();
        for (Player p : selected) selectedById.put(p.getId(), p);
        String captainId = Objects.toString(data.get("captainId"), null);
        Player captain = captainId == null ? null : squadById.get(captainId);
        if (captain == null && captainId != null) captain = replacementByOriginalId.get(captainId);
        if (captain == null) captain = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(null);
        lineup.setCaptain(captain);
        String keeperId = Objects.toString(data.get("keeperId"), null);
        Player keeper = keeperId == null ? null : squadById.get(keeperId);
        if (keeper == null && keeperId != null) keeper = replacementByOriginalId.get(keeperId);
        if (keeper == null || !selectedById.containsKey(keeper.getId())) {
            keeper = selected.stream().filter(p -> "KEEPER".equals(p.getRole()))
                    .max(Comparator.comparingDouble(Player::getKeeperRating))
                    .orElse(selected.stream().max(Comparator.comparingDouble(Player::getKeeperRating)).orElse(null));
        }
        lineup.setKeeper(keeper);
        List<Map<String, Object>> rawBowling = (List<Map<String, Object>>) data.get("bowlingOrders");
        int planOvers = "FC".equalsIgnoreCase(format) ? 100 : MatchFormatRules.getMaxOvers(format);
        if (rawBowling != null) {
            rawBowling.sort(Comparator.comparingInt(b -> ((Number) b.getOrDefault("overNumber", 999)).intValue()));
            for (Map<String, Object> bo : rawBowling) {
                int on = ((Number) bo.getOrDefault("overNumber", 0)).intValue();
                if (on < 1 || on > planOvers) continue;
                String origId = Objects.toString(bo.get("bowlerId"), null);
                Player bowler = null;
                if (origId != null) {
                    Player direct = squadById.get(origId);
                    if (direct != null && selectedById.containsKey(direct.getId())) bowler = direct;
                    else {
                        Player rep = replacementByOriginalId.get(origId);
                        if (rep != null && selectedById.containsKey(rep.getId())) bowler = rep;
                        else bowler = pickSelectedByRole(selected, resolveRole(origId));
                    }
                }
                if (bowler == null) bowler = selected.stream().max(Comparator.comparingDouble(Player::getBowlRating)).orElse(null);
                if (bowler == null) continue;
                lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(on).bowler(bowler)
                        .aggression(Objects.toString(bo.getOrDefault("aggression", bowler.getBowlAggression()), "N")).build());
            }
        }
        if (lineup.getBowlingOrders().isEmpty()) {
            List<Player> top5 = selected.stream().sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
            if (top5.isEmpty()) return null;
            for (int ov = 1; ov <= planOvers; ov++) {
                Player bowler = top5.get((ov - 1) % top5.size());
                lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(ov)
                        .bowler(bowler).aggression(bowler.getBowlAggression()).build());
            }
        }
        lineup.getBowlingOrders().sort(Comparator.comparingInt(BowlingOrder::getOverNumber));
        return matchLineupRepository.save(lineup);
    }

    /**
     * Repair an already-saved match lineup so it never fields a player who has left
     * the squad. Removed players are replaced slot-for-slot by the best available
     * player of the same role, preserving batting positions, aggression and the
     * bowling plan. Returns true if anything changed (caller persists).
     */
    @Transactional
    public boolean sanitizeLineup(MatchLineup lineup, Team team) {
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) return false;
        Map<UUID, Player> squadById = new HashMap<>();
        for (Player p : squad) squadById.put(p.getId(), p);

        List<LineupPlayer> players = lineup.getPlayers();
        if (players == null || players.isEmpty()) return false;
        List<LineupPlayer> ordered = new ArrayList<>(players);
        ordered.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        Set<UUID> used = new HashSet<>();
        Map<UUID, Player> replacementByOriginal = new HashMap<>();
        List<Player> selected = new ArrayList<>();
        boolean changed = false;

        for (LineupPlayer lp : ordered) {
            Player current = lp.getPlayer();
            Player chosen = current != null ? squadById.get(current.getId()) : null;
            if (chosen == null || used.contains(chosen.getId())) {
                String role = current != null ? current.getRole() : null;
                chosen = pickReplacementByRole(squad, used, role);
                if (chosen == null) chosen = pickBestRemainingPlayer(squad, used);
                if (chosen == null) continue;
                if (current != null) replacementByOriginal.put(current.getId(), chosen);
                lp.setPlayer(chosen);
                lp.setBatAggression(chosen.getBatAggression());
                changed = true;
            }
            used.add(chosen.getId());
            selected.add(chosen);
        }

        Map<UUID, Player> selectedById = new HashMap<>();
        for (Player p : selected) selectedById.put(p.getId(), p);

        Player captain = lineup.getCaptain();
        if (captain == null || !squadById.containsKey(captain.getId())) {
            Player rep = captain != null ? replacementByOriginal.get(captain.getId()) : null;
            if (rep == null) rep = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(null);
            if (rep != null) { lineup.setCaptain(rep); changed = true; }
        }

        Player keeper = lineup.getKeeper();
        if (keeper == null || !selectedById.containsKey(keeper.getId())) {
            Player rep = keeper != null ? replacementByOriginal.get(keeper.getId()) : null;
            if (rep == null) rep = selected.stream().filter(p -> "KEEPER".equals(p.getRole()))
                    .max(Comparator.comparingDouble(Player::getKeeperRating))
                    .orElse(selected.stream().max(Comparator.comparingDouble(Player::getKeeperRating)).orElse(null));
            if (rep != null) { lineup.setKeeper(rep); changed = true; }
        }

        if (lineup.getBowlingOrders() != null) {
            Player defaultBowler = selected.stream().max(Comparator.comparingDouble(Player::getBowlRating)).orElse(null);
            for (BowlingOrder bo : lineup.getBowlingOrders()) {
                Player bowler = bo.getBowler();
                if (bowler != null && selectedById.containsKey(bowler.getId())) continue;
                Player rep = null;
                if (bowler != null) {
                    rep = replacementByOriginal.get(bowler.getId());
                    if (rep != null && !selectedById.containsKey(rep.getId())) rep = null;
                }
                if (rep == null) rep = pickSelectedByRole(selected, bowler != null ? bowler.getRole() : null);
                if (rep == null) rep = defaultBowler;
                if (rep != null) { bo.setBowler(rep); changed = true; }
            }
        }
        return changed;
    }

    /**
     * Repair a team's stored default lineups so they never reference a player who
     * has left the squad (fired, retired, or sold). Each removed player is replaced
     * slot-for-slot by the best available player of the same role, preserving the
     * rest of the saved order. Call this whenever a player leaves the team so the
     * stored default stays current and never needs regenerating from scratch.
     *
     * @return the number of default lineups that were updated
     */
    @Transactional
    public int healDefaultLineups(Team team) {
        if (team == null || team.getId() == null) return 0;
        int updated = 0;
        for (String format : List.of("T20", "ODI", "FC")) {
            if (healDefaultLineup(team, format)) updated++;
        }
        return updated;
    }

    /**
     * Repair a single stored default lineup for the given format.
     *
     * @return true if the stored data was changed
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public boolean healDefaultLineup(Team team, String format) {
        Optional<DefaultLineup> opt = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (opt.isEmpty()) return false;
        DefaultLineup def = opt.get();
        Map<String, Object> data = def.getLineupData();
        if (data == null) return false;

        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) return false;
        Map<String, Player> squadById = new HashMap<>();
        for (Player p : squad) squadById.put(p.getId().toString(), p);

        List<Map<String, Object>> rawPlayers = (List<Map<String, Object>>) data.get("players");
        if (rawPlayers == null || rawPlayers.isEmpty()) return false;

        // Build a fresh object graph (Hibernate JSON dirty-checking is unreliable
        // for in-place mutations of the mapped Map).
        Map<String, Object> newData = new LinkedHashMap<>(data);
        List<Map<String, Object>> newPlayers = new ArrayList<>(rawPlayers.size());

        Set<UUID> usedIds = new HashSet<>();
        Map<String, String> replacementByOriginalId = new HashMap<>();
        List<Player> selected = new ArrayList<>();
        boolean changed = false;

        for (Map<String, Object> slot : rawPlayers) {
            Map<String, Object> copy = new LinkedHashMap<>(slot);
            String playerId = Objects.toString(copy.get("playerId"), null);
            Player chosen = playerId == null ? null : squadById.get(playerId);
            if (chosen == null || usedIds.contains(chosen.getId())) {
                String requiredRole = resolveRole(playerId);
                chosen = pickReplacementByRole(squad, usedIds, requiredRole);
                if (chosen == null) chosen = pickBestRemainingPlayer(squad, usedIds);
                if (chosen == null) { newPlayers.add(copy); continue; }
                if (playerId != null) replacementByOriginalId.put(playerId, chosen.getId().toString());
                copy.put("playerId", chosen.getId().toString());
                copy.put("batAggression", chosen.getBatAggression());
                changed = true;
            }
            usedIds.add(chosen.getId());
            selected.add(chosen);
            newPlayers.add(copy);
        }
        newData.put("players", newPlayers);

        Map<UUID, Player> selectedById = new HashMap<>();
        for (Player p : selected) selectedById.put(p.getId(), p);

        // Captain
        String captainId = Objects.toString(newData.get("captainId"), null);
        if (captainId != null && !squadById.containsKey(captainId)) {
            Player rep = squadById.get(replacementByOriginalId.get(captainId));
            if (rep == null) rep = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(null);
            if (rep != null) { newData.put("captainId", rep.getId().toString()); changed = true; }
        }

        // Wicket-keeper
        String keeperId = Objects.toString(newData.get("keeperId"), null);
        if (keeperId != null && !squadById.containsKey(keeperId)) {
            Player rep = squadById.get(replacementByOriginalId.get(keeperId));
            if (rep == null) rep = selected.stream().filter(p -> "KEEPER".equals(p.getRole()))
                    .max(Comparator.comparingDouble(Player::getKeeperRating))
                    .orElse(selected.stream().max(Comparator.comparingDouble(Player::getKeeperRating)).orElse(null));
            if (rep != null) { newData.put("keeperId", rep.getId().toString()); changed = true; }
        }

        // Bowling orders
        List<Map<String, Object>> rawBowling = (List<Map<String, Object>>) data.get("bowlingOrders");
        if (rawBowling != null) {
            Player defaultBowler = selected.stream().max(Comparator.comparingDouble(Player::getBowlRating)).orElse(null);
            List<Map<String, Object>> newBowling = new ArrayList<>(rawBowling.size());
            for (Map<String, Object> bo : rawBowling) {
                Map<String, Object> copy = new LinkedHashMap<>(bo);
                String bowlerId = Objects.toString(copy.get("bowlerId"), null);
                if (bowlerId != null) {
                    Player direct = squadById.get(bowlerId);
                    boolean keep = direct != null && selectedById.containsKey(direct.getId());
                    if (!keep) {
                        Player rep = null;
                        if (direct == null) {
                            rep = squadById.get(replacementByOriginalId.get(bowlerId));
                            if (rep != null && !selectedById.containsKey(rep.getId())) rep = null;
                        }
                        if (rep == null) rep = pickSelectedByRole(selected, resolveRole(bowlerId));
                        if (rep == null) rep = defaultBowler;
                        if (rep != null) { copy.put("bowlerId", rep.getId().toString()); changed = true; }
                    }
                }
                newBowling.add(copy);
            }
            newData.put("bowlingOrders", newBowling);
        }

        if (changed) {
            def.setLineupData(newData);
            defaultLineupRepository.save(def);
            log.info("Healed default lineup for team {} ({})", team.getTeamName(), format);
        }
        return changed;
    }

    public String resolveRole(String playerId) {
        if (playerId == null) return null;
        try { return playerRepository.findById(UUID.fromString(playerId)).map(Player::getRole).orElse(null); }
        catch (IllegalArgumentException e) { return null; }
    }
    public Player pickReplacementByRole(List<Player> squad, Set<UUID> usedIds, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comp = switch (role) {
            case "BATSMAN"     -> Comparator.comparingDouble(Player::getBatRating);
            case "BOWLER"      -> Comparator.comparingDouble(Player::getBowlRating);
            case "KEEPER"      -> Comparator.comparingDouble(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingDouble(p -> p.getBatRating() + p.getBowlRating());
            default            -> Comparator.comparingInt(Player::getRating);
        };
        return squad.stream().filter(p -> !usedIds.contains(p.getId()) && role.equalsIgnoreCase(p.getRole())).max(comp).orElse(null);
    }
    public Player pickBestRemainingPlayer(List<Player> squad, Set<UUID> usedIds) {
        return squad.stream().filter(p -> !usedIds.contains(p.getId())).max(Comparator.comparingInt(Player::getRating)).orElse(null);
    }
    public Player pickSelectedByRole(List<Player> selected, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comp = switch (role) {
            case "BATSMAN"     -> Comparator.comparingDouble(Player::getBatRating);
            case "BOWLER"      -> Comparator.comparingDouble(Player::getBowlRating);
            case "KEEPER"      -> Comparator.comparingDouble(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingDouble(p -> p.getBatRating() + p.getBowlRating());
            default            -> Comparator.comparingInt(Player::getRating);
        };
        return selected.stream().filter(p -> role.equalsIgnoreCase(p.getRole())).max(comp).orElse(null);
    }
    public List<Player> autoSelectPlaying11(List<Player> squad) {
        List<Player> selected = new ArrayList<>();
        Set<UUID> pickedIds   = new HashSet<>();
        squad.stream().filter(p -> "KEEPER".equals(p.getRole())).max(Comparator.comparingDouble(Player::getKeeperRating))
                .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });
        if (selected.isEmpty()) squad.stream().max(Comparator.comparingDouble(Player::getKeeperRating))
                .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "BATSMAN".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Double.compare(b.getBatRating(), a.getBatRating())).limit(5)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "ALL_ROUNDER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Double.compare(b.getBatRating() + b.getBowlRating(), a.getBatRating() + a.getBowlRating())).limit(2)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "BOWLER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).limit(3)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        if (selected.size() < 11) squad.stream().filter(p -> !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getRating(), a.getRating())).limit(11 - selected.size())
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        return selected.subList(0, Math.min(11, selected.size()));
    }
    public List<Player> buildAutoBattingOrder(List<Player> selected) {
        List<Player> order = new ArrayList<>();
        selected.stream().filter(p -> "BATSMAN".equals(p.getRole()))
                .sorted((a, b) -> Double.compare(b.getBatRating(), a.getBatRating())).forEach(order::add);
        selected.stream().filter(p -> "KEEPER".equals(p.getRole())).findFirst().ifPresent(order::add);
        selected.stream().filter(p -> "ALL_ROUNDER".equals(p.getRole()))
                .sorted((a, b) -> Double.compare(b.getBatRating(), a.getBatRating())).forEach(order::add);
        selected.stream().filter(p -> "BOWLER".equals(p.getRole()))
                .sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).forEach(order::add);
        selected.stream().filter(p -> !order.contains(p)).forEach(order::add);
        return order;
    }

}
