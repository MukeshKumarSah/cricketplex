package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LineupService {

    private final MatchLineupRepository lineupRepository;
    private final DefaultLineupRepository defaultLineupRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final WeatherService weatherService;

    /* ═══ Bowling templates ═══ */

    // T20: 20 overs, 5 bowlers (1-indexed), each bowls exactly 4, no consecutive
    private static final int[][] T20_BALANCED   = {{1,2,3,4,5,1,2,3,4,5,1,2,3,4,5,1,2,3,4,5}};
    private static final int[][] T20_PACE_HEAVY = {{1,2,1,3,2,4,5,4,3,5,1,4,5,3,2,1,5,4,3,2}};
    private static final int[][] T20_SPIN_HEAVY = {{1,3,2,4,1,3,5,4,3,5,2,4,5,1,4,2,5,3,1,2}};

    // ODI: 50 overs, 5 bowlers, each max 10, no consecutive
    private static final int[][] ODI_BALANCED   = generateOdiCyclic();
    private static final int[][] ODI_PACE_HEAVY = generateOdiPaceHeavy();
    private static final int[][] ODI_SPIN_HEAVY = generateOdiSpinHeavy();

    // FC: no predefined templates — users create custom 100-over plans

    private static int[][] generateOdiCyclic() {
        int[] order = new int[50];
        for (int i = 0; i < 50; i++) order[i] = (i % 5) + 1;
        return new int[][]{order};
    }

    private static int[][] generateOdiPaceHeavy() {
        // B1,B2 = pace (10 each), B3 = medium (10), B4,B5 = spin (10 each)
        // Pace heavy at powerplay (1-10) and death (41-50), spin in middle
        int[] o = new int[50];
        // Use a pattern: first 10 overs pace-heavy, middle spin-heavy, death pace
        int[] pattern = {
            1,2,1,3,2,1,4,2,3,5,  // overs 1-10: mostly pace (B1,B2)
            4,3,5,4,3,5,4,3,5,3,  // overs 11-20: spin/medium
            4,5,3,4,5,3,4,5,3,5,  // overs 21-30: spin/medium
            4,3,5,1,2,1,3,2,5,4,  // overs 31-40: transition
            1,2,1,2,1,2,1,2,3,4   // overs 41-50: pace death
        };
        // Verify counts and fix: B1=10, B2=10, B3=10, B4=10, B5=10
        System.arraycopy(pattern, 0, o, 0, 50);
        return new int[][]{o};
    }

    private static int[][] generateOdiSpinHeavy() {
        int[] o = new int[50];
        int[] pattern = {
            1,3,2,4,1,5,2,3,4,5,  // overs 1-10
            3,4,5,3,4,5,3,4,5,3,  // overs 11-20: spin heavy
            4,5,3,4,5,3,4,5,1,2,  // overs 21-30
            3,4,5,1,3,2,4,5,1,2,  // overs 31-40
            1,2,1,2,5,1,2,3,4,5   // overs 41-50
        };
        System.arraycopy(pattern, 0, o, 0, 50);
        return new int[][]{o};
    }



    public Map<String, int[]> getTemplatesForFormat(String format) {
        Map<String, int[]> templates = new LinkedHashMap<>();
        if ("T20".equalsIgnoreCase(format)) {
            templates.put("BALANCED", T20_BALANCED[0]);
            templates.put("PACE_HEAVY", T20_PACE_HEAVY[0]);
            templates.put("SPIN_HEAVY", T20_SPIN_HEAVY[0]);
        } else if ("ODI".equalsIgnoreCase(format)) {
            templates.put("BALANCED", ODI_BALANCED[0]);
            templates.put("PACE_HEAVY", ODI_PACE_HEAVY[0]);
            templates.put("SPIN_HEAVY", ODI_SPIN_HEAVY[0]);
        }
        // FC: no templates — fully custom 100-over plans
        return templates;
    }

    /* ═══ Load lineup data for a fixture ═══ */

    @Transactional(readOnly = true)
    public Map<String, Object> getLineupData(UUID fixtureId, User user) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture not found"));

        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        League league = fixture.getLeague();
        boolean isFriendly = "FRIENDLY".equals(fixture.getMatchType());
        String format = isFriendly ? fixture.getFormat() : league.getFormat();
        boolean isHome = fixture.getHomeTeam().getId().equals(myTeam.getId());
        boolean isAway = fixture.getAwayTeam().getId().equals(myTeam.getId());
        if (!isHome && !isAway) {
            throw new IllegalArgumentException("This is not your match");
        }

        // Match info
        Map<String, Object> matchInfo = new LinkedHashMap<>();
        matchInfo.put("fixtureId", fixture.getId());
        matchInfo.put("homeTeamName", fixture.getHomeTeam().getTeamName());
        matchInfo.put("homeTeamPicUrl", fixture.getHomeTeam().getTeamProfilePicUrl());
        matchInfo.put("awayTeamName", fixture.getAwayTeam().getTeamName());
        matchInfo.put("awayTeamPicUrl", fixture.getAwayTeam().getTeamProfilePicUrl());
        matchInfo.put("format", format);
        if (isFriendly) {
            matchInfo.put("leagueId", null);
            matchInfo.put("leagueLabel", "Friendly");
            matchInfo.put("matchType", "FRIENDLY");
        } else {
            matchInfo.put("leagueId", league.getId());
            matchInfo.put("leagueLabel", league.getCountry() + " Div " + league.getDivision() + "." + league.getLeagueNumber());
            matchInfo.put("matchType", "LEAGUE");
        }
        matchInfo.put("matchDate", fixture.getMatchDate().toString());
        matchInfo.put("groundName", isHome ? fixture.getHomeTeam().getGroundName() : fixture.getAwayTeam().getGroundName());
        matchInfo.put("pitchType", fixture.getPitchType());
        matchInfo.put("isHome", isHome);
        matchInfo.put("round", fixture.getRound());
        matchInfo.put("status", fixture.getStatus());
        matchInfo.put("weather", weatherService.getMatchWeather(
                fixture.getHomeTeam().getCountry(), fixture.getMatchDate(), LocalDate.now()));

        // Squad players
        List<Player> squad = playerRepository.findByTeam(myTeam);
        List<Map<String, Object>> playerList = new ArrayList<>();
        for (Player p : squad) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("firstName", p.getFirstName());
            pm.put("lastName", p.getLastName());
            pm.put("role", p.getRole());
            pm.put("batHand", p.getBatHand());
            pm.put("bowlHand", p.getBowlHand());
            pm.put("bowlType", p.getBowlType());
            pm.put("batRating", p.getBatRating());
            pm.put("bowlRating", p.getBowlRating());
            pm.put("keeperRating", p.getKeeperRating());
            pm.put("fldRating", p.getFldRating());
            pm.put("stamina", p.getStamina());
            pm.put("experience", p.getExperience());
            pm.put("fitness", p.getFitness());
            pm.put("confidence", p.getConfidence());
            pm.put("rating", p.getRating());
            pm.put("age", p.getAge());
            pm.put("ageDays", p.getAgeDays());
            pm.put("batAggression", p.getBatAggression());
            pm.put("bowlAggression", p.getBowlAggression());
            playerList.add(pm);
        }

        // Saved lineup (if exists)
        Map<String, Object> savedLineup = null;
        Optional<MatchLineup> existing = lineupRepository.findByFixtureIdAndTeamId(fixtureId, myTeam.getId());
        if (existing.isPresent()) {
            MatchLineup ml = existing.get();
            savedLineup = new LinkedHashMap<>();
            savedLineup.put("captainId", ml.getCaptain() != null ? ml.getCaptain().getId() : null);
            savedLineup.put("keeperId", ml.getKeeper() != null ? ml.getKeeper().getId() : null);
            savedLineup.put("tossChoice", ml.getTossChoice());
            savedLineup.put("batOrBowl", ml.getBatOrBowl());
            savedLineup.put("bowlingPlan", ml.getBowlingPlan());

            List<Map<String, Object>> savedPlayers = new ArrayList<>();
            for (LineupPlayer lp : ml.getPlayers()) {
                Map<String, Object> sp = new LinkedHashMap<>();
                sp.put("playerId", lp.getPlayer().getId());
                sp.put("battingPosition", lp.getBattingPosition());
                sp.put("batAggression", lp.getBatAggression());
                savedPlayers.add(sp);
            }
            savedPlayers.sort(Comparator.comparing(m -> (Integer) m.get("battingPosition")));
            savedLineup.put("players", savedPlayers);

            List<Map<String, Object>> savedBowling = new ArrayList<>();
            for (BowlingOrder bo : ml.getBowlingOrders()) {
                Map<String, Object> sb = new LinkedHashMap<>();
                sb.put("overNumber", bo.getOverNumber());
                sb.put("bowlerId", bo.getBowler().getId());
                sb.put("aggression", bo.getAggression());
                savedBowling.add(sb);
            }
            savedBowling.sort(Comparator.comparing(m -> (Integer) m.get("overNumber")));
            savedLineup.put("bowlingOrders", savedBowling);
        }

        // Default lineup (returned when no saved lineup for this fixture)
        Map<String, Object> defaultLineup = null;
        if (savedLineup == null) {
            Optional<DefaultLineup> defOpt = defaultLineupRepository.findByTeamIdAndFormat(myTeam.getId(), format);
            if (defOpt.isPresent()) {
                defaultLineup = defOpt.get().getLineupData();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchInfo", matchInfo);
        result.put("squad", playerList);
        result.put("savedLineup", savedLineup);
        result.put("defaultLineup", defaultLineup);
        return result;
    }

    /* ═══ Save lineup ═══ */

    @Transactional
    public Map<String, Object> saveLineup(UUID fixtureId, User user, Map<String, Object> request) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture not found"));

        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        boolean isHome = fixture.getHomeTeam().getId().equals(myTeam.getId());
        boolean isAway = fixture.getAwayTeam().getId().equals(myTeam.getId());
        if (!isHome && !isAway) throw new IllegalArgumentException("Not your match");
        if (!"SCHEDULED".equals(fixture.getStatus())) throw new IllegalArgumentException("Match already started");

        // Upsert lineup
        MatchLineup lineup = lineupRepository.findByFixtureIdAndTeamId(fixtureId, myTeam.getId())
                .orElse(MatchLineup.builder().fixture(fixture).team(myTeam).build());

        // Captain & Keeper
        String captainId = (String) request.get("captainId");
        String keeperId = (String) request.get("keeperId");
        if (captainId != null) {
            lineup.setCaptain(playerRepository.findById(UUID.fromString(captainId)).orElse(null));
        }
        if (keeperId != null) {
            lineup.setKeeper(playerRepository.findById(UUID.fromString(keeperId)).orElse(null));
        }

        // Toss choice (away team picks heads/tails; home always null)
        if (isAway) {
            lineup.setTossChoice((String) request.get("tossChoice"));
        }
        // Bat or bowl preference (both home and away)
        lineup.setBatOrBowl((String) request.get("batOrBowl"));

        // Bowling plan type
        String bowlingPlan = (String) request.get("bowlingPlan");
        if (bowlingPlan != null) lineup.setBowlingPlan(bowlingPlan);

        // Save the lineup first to get ID
        lineup = lineupRepository.save(lineup);

        // Clear old players and bowling orders
        lineup.getPlayers().clear();
        lineup.getBowlingOrders().clear();
        lineupRepository.flush();

        // Playing 11
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) request.get("players");
        if (players != null) {
            String format = (fixture.getLeague() != null) ? fixture.getLeague().getFormat() : fixture.getFormat();
            int maxPlayers = 11;
            int count = 0;
            Set<String> playerIdSet = new HashSet<>();
            for (Map<String, Object> pm : players) {
                if (count >= maxPlayers) break;
                String pid = (String) pm.get("playerId");
                if (pid == null || playerIdSet.contains(pid)) continue;
                playerIdSet.add(pid);
                Player player = playerRepository.findById(UUID.fromString(pid)).orElse(null);
                if (player == null || !player.getTeam().getId().equals(myTeam.getId())) continue;

                LineupPlayer lp = LineupPlayer.builder()
                        .lineup(lineup)
                        .player(player)
                        .battingPosition(((Number) pm.get("battingPosition")).intValue())
                        .batAggression(pm.getOrDefault("batAggression", "N").toString())
                        .build();
                lineup.getPlayers().add(lp);
                count++;
            }

            // Bowling orders
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bowlingOrders = (List<Map<String, Object>>) request.get("bowlingOrders");
            if (bowlingOrders != null) {
                int maxOvers = "T20".equalsIgnoreCase(format) ? 20 : "ODI".equalsIgnoreCase(format) ? 50 : 100;
                int maxPerBowler = "T20".equalsIgnoreCase(format) ? 4 : "ODI".equalsIgnoreCase(format) ? 10 : 30;
                Map<String, Integer> bowlerOverCounts = new HashMap<>();

                for (Map<String, Object> bo : bowlingOrders) {
                    int overNum = ((Number) bo.get("overNumber")).intValue();
                    if (overNum < 1 || overNum > maxOvers) continue;
                    String bid = (String) bo.get("bowlerId");
                    if (bid == null) continue;
                    // Bowler must be in playing 11
                    if (!playerIdSet.contains(bid)) continue;

                    int currentCount = bowlerOverCounts.getOrDefault(bid, 0);
                    if (currentCount >= maxPerBowler) continue;
                    bowlerOverCounts.put(bid, currentCount + 1);

                    Player bowler = playerRepository.findById(UUID.fromString(bid)).orElse(null);
                    if (bowler == null) continue;

                    BowlingOrder order = BowlingOrder.builder()
                            .lineup(lineup)
                            .overNumber(overNum)
                            .bowler(bowler)
                            .aggression(bo.getOrDefault("aggression", "N").toString())
                            .build();
                    lineup.getBowlingOrders().add(order);
                }
            }
        }

        lineupRepository.save(lineup);

        // Save as default if requested
        Object saveAsDefault = request.get("saveAsDefault");
        if (Boolean.TRUE.equals(saveAsDefault)) {
            String fmt = (fixture.getLeague() != null) ? fixture.getLeague().getFormat() : fixture.getFormat();
            saveDefaultLineup(myTeam, fmt, request);
        }

        return Map.of("id", lineup.getId(), "status", "saved");
    }

    /* ═══ Default lineup ═══ */

    @Transactional
    public void saveDefaultLineup(Team team, String format, Map<String, Object> lineupData) {
        // Strip fixture-specific fields, keep the order/plan
        Map<String, Object> data = new LinkedHashMap<>(lineupData);
        data.remove("saveAsDefault");
        data.remove("tossChoice");

        DefaultLineup def = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format)
                .orElse(DefaultLineup.builder().team(team).format(format).build());

        // Preserve the existing batOrBowl preference if the new save didn't set one
        // (Home team games don't require a toss call, so batOrBowl may arrive as null;
        //  we keep the value from the last away game rather than overwriting with null)
        if (data.get("batOrBowl") == null && def.getLineupData() != null) {
            Object prev = def.getLineupData().get("batOrBowl");
            if (prev != null) data.put("batOrBowl", prev);
        }

        def.setLineupData(data);
        defaultLineupRepository.save(def);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDefaultLineup(User user) {
        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        // Return all format defaults
        Map<String, Object> result = new LinkedHashMap<>();
        for (String fmt : List.of("T20", "ODI", "FC")) {
            Optional<DefaultLineup> opt = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), fmt);
            opt.ifPresent(d -> result.put(fmt, d.getLineupData()));
        }
        if (result.isEmpty()) return Map.of("found", false);
        result.put("found", true);
        return result;
    }
}
