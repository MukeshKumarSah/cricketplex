package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import com.cricketplex.repository.DefaultLineupRepository;
import com.cricketplex.repository.MatchLineupRepository;
import com.cricketplex.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        if (existing.isPresent()) return existing.get();
        Optional<DefaultLineup> savedDefault = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (savedDefault.isPresent()) {
            MatchLineup fromDefault = buildLineupFromDefault(fixture, team, format, savedDefault.get());
            if (fromDefault != null) return fromDefault;
        }
        log.info("No lineup found for team {} — auto-generating", team.getTeamName());
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) throw new IllegalStateException("Team " + team.getTeamName() + " has fewer than 11 players (" + squad.size() + ")");
        List<Player> selected     = autoSelectPlaying11(squad);
        List<Player> battingOrder = buildAutoBattingOrder(selected);
        MatchLineup lineup = MatchLineup.builder().fixture(fixture).team(team).bowlingPlan("BALANCED").build();
        Player keeper = selected.stream().filter(p -> "KEEPER".equals(p.getRole())).max(Comparator.comparingDouble(Player::getKeeperRating))
                .orElse(selected.stream().max(Comparator.comparingDouble(Player::getKeeperRating)).orElse(selected.get(0)));
        lineup.setKeeper(keeper);
        Player captain = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(selected.get(0));
        lineup.setCaptain(captain);
        for (int i = 0; i < battingOrder.size(); i++) {
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(battingOrder.get(i))
                    .battingPosition(i + 1).batAggression(battingOrder.get(i).getBatAggression()).build());
        }
        List<Player> topBowlers;
        if ("FC".equalsIgnoreCase(format)) {
            topBowlers = selected.stream().filter(p -> p.getBowlRating() >= 15)
                    .sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).toList();
            if (topBowlers.size() < 5) topBowlers = selected.stream()
                    .sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
        } else {
            topBowlers = selected.stream().sorted((a, b) -> Double.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
        }
        int bowlingPlanOvers = "FC".equalsIgnoreCase(format) ? 100 : MatchFormatRules.getMaxOvers(format);
        for (int over = 1; over <= bowlingPlanOvers; over++) {
            Player bowler = topBowlers.get((over - 1) % topBowlers.size());
            lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(over)
                    .bowler(bowler).aggression(bowler.getBowlAggression()).build());
        }
        return matchLineupRepository.save(lineup);
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
