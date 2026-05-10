package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final SimSessionRepository simSessionRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final MatchResultRepository matchResultRepository;
    private final BallEventRepository ballEventRepository;
    private final InningsRepository inningsRepository;
    private final BattingScorecardRepository battingScorecardRepository;
    private final BowlingScorecardRepository bowlingScorecardRepository;
    private final LineupPlayerRepository lineupPlayerRepository;
    private final BowlingOrderRepository bowlingOrderRepository;
    private final MatchEngine matchEngine;
    private final ObjectMapper objectMapper;

    /** Self-reference for @Async proxy to work on internal calls. */
    @Lazy @Autowired
    private SimulationService self;

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /** Creates a SimSession record and kicks off async simulation. */
    public SimSession createSession(String name, String format, int numMatches, String configJson) {
        SimSession session = SimSession.builder()
                .name(name)
                .format(format.toUpperCase())
                .numMatches(numMatches)
                .configJson(configJson)
                .status("PENDING")
                .build();
        SimSession saved = simSessionRepository.save(session);
        self.runSimAsync(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SimSession> listSessions() {
        return simSessionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<SimSession> getSession(UUID id) {
        return simSessionRepository.findById(id);
    }

    /** Deletes all data related to a simulation session. */
    @Transactional
    public void deleteSession(UUID sessionId) {
        // Delete in FK-safe leaf-first order via bulk JPQL deletes
        ballEventRepository.deleteBySimSessionId(sessionId);
        battingScorecardRepository.deleteBySimSessionId(sessionId);
        bowlingScorecardRepository.deleteBySimSessionId(sessionId);
        inningsRepository.deleteBySimSessionId(sessionId);
        matchResultRepository.deleteBySimSessionId(sessionId);
        lineupPlayerRepository.deleteBySimSessionId(sessionId);
        bowlingOrderRepository.deleteBySimSessionId(sessionId);
        matchLineupRepository.deleteBySimSessionId(sessionId);
        fixtureRepository.deleteBySimSessionId(sessionId);
        playerRepository.deleteAll(playerRepository.findBySimSessionId(sessionId));
        teamRepository.deleteAll(teamRepository.findBySimSessionId(sessionId));
        simSessionRepository.deleteById(sessionId);
    }

    // ─────────────────────────────────────────────────────────────────
    // Async runner
    // ─────────────────────────────────────────────────────────────────

    @Async
    public void runSimAsync(UUID sessionId) {
        try {
            runSimulation(sessionId);
        } catch (Exception e) {
            log.error("Sim session {} failed", sessionId, e);
            simSessionRepository.findById(sessionId).ifPresent(s -> {
                s.setStatus("ERROR");
                s.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 999)) : "Unknown error");
                simSessionRepository.save(s);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Core simulation logic (runs in its own transactions per match)
    // ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void runSimulation(UUID sessionId) throws Exception {
        SimSession session = simSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        session.setStatus("RUNNING");
        simSessionRepository.save(session);

        Map<String, Object> config = objectMapper.readValue(session.getConfigJson(), Map.class);

        String format = session.getFormat();
        int numMatches = session.getNumMatches();
        int maxOvers = "T20".equalsIgnoreCase(format) ? 20 : 50;
        String pitchType = (String) config.getOrDefault("pitchType", "STANDARD");
        String country = (String) config.getOrDefault("country", "England");

        List<Map<String, Object>> playersACfg = (List<Map<String, Object>>) config.get("playersA");
        List<Map<String, Object>> playersBCfg = (List<Map<String, Object>>) config.get("playersB");

        // Create teams
        Team teamA = createSimTeam((String) config.get("teamAName"), country, sessionId);
        Team teamB = createSimTeam((String) config.get("teamBName"), country, sessionId);

        // Create players
        List<Player> playersA = createSimPlayers(playersACfg, teamA, sessionId);
        List<Player> playersB = createSimPlayers(playersBCfg, teamB, sessionId);

        // Stat accumulators per player UUID
        Map<UUID, BatStats> batMap = new LinkedHashMap<>();
        Map<UUID, BowlStats> bowlMap = new LinkedHashMap<>();
        Map<UUID, FldStats> fldMap = new LinkedHashMap<>();
        for (Player p : playersA) { batMap.put(p.getId(), new BatStats(p)); bowlMap.put(p.getId(), new BowlStats(p)); fldMap.put(p.getId(), new FldStats(p)); }
        for (Player p : playersB) { batMap.put(p.getId(), new BatStats(p)); bowlMap.put(p.getId(), new BowlStats(p)); fldMap.put(p.getId(), new FldStats(p)); }

        int teamAWins = 0, teamBWins = 0, draws = 0;
        int batFirstWins = 0, batSecondWins = 0;
        long totalInn1Runs = 0, totalInn1Wkts = 0, totalInn2Runs = 0, totalInn2Wkts = 0;

        // Score distribution buckets: <100,100-150,150-200,200-250,250-300,300-350,350+
        int[] distCount = new int[7];
        int[] distChaseWins = new int[7];

        // Progressive scores: sums over N matches (milestones: 5,10,15,20 for T20 or 10,20,30,40,50 for ODI)
        int[] milestones = "T20".equalsIgnoreCase(format) ? new int[]{5, 10, 15, 20} : new int[]{10, 20, 30, 40, 50};
        long[] progA1 = new long[milestones.length], progA2 = new long[milestones.length];
        long[] progB1 = new long[milestones.length], progB2 = new long[milestones.length];

        List<UUID> fixtureIds = new ArrayList<>();

        for (int matchNum = 0; matchNum < numMatches; matchNum++) {
            UUID fixtureId = createSimFixture(teamA, teamB, format, pitchType, sessionId);
            fixtureIds.add(fixtureId);
            createSimLineup(fixtureId, teamA, teamB, playersA, playersB, playersACfg, playersBCfg, maxOvers);

            // Run match in its own transactional context
            matchEngine.simulateMatch(fixtureId);

            // Read all scorecard/ball-event data inside a transaction so lazy
            // collections can be initialised; also accumulates per-player stats.
            MatchReadResult mr = self.readAndAccumulate(fixtureId, milestones, batMap, bowlMap, fldMap);
            if (!mr.hasInnings) continue;

            totalInn1Runs += mr.inn1Runs;
            totalInn1Wkts += mr.inn1Wkts;
            totalInn2Runs += mr.inn2Runs;
            totalInn2Wkts += mr.inn2Wkts;

            if (mr.winnerId == null) {
                draws++;
            } else if (mr.winnerId.equals(teamA.getId())) {
                teamAWins++;
            } else {
                teamBWins++;
            }

            if (mr.inn1BatTeamId != null && mr.inn2BatTeamId != null && mr.winnerId != null) {
                if (mr.winnerId.equals(mr.inn1BatTeamId)) {
                    batFirstWins++;
                } else {
                    batSecondWins++;
                }
            }

            if (mr.inn1BatTeamId != null) {
                int bucket = scoreBucket(mr.inn1Runs);
                distCount[bucket]++;
                if (mr.winnerId != null && mr.inn2BatTeamId != null && mr.winnerId.equals(mr.inn2BatTeamId)) {
                    distChaseWins[bucket]++;
                }
            }

            if (mr.prog1 != null && mr.inn1BatTeamId != null) {
                if (mr.inn1BatTeamId.equals(teamA.getId())) {
                    for (int i = 0; i < milestones.length; i++) progA1[i] += mr.prog1[i];
                } else {
                    for (int i = 0; i < milestones.length; i++) progB1[i] += mr.prog1[i];
                }
            }
            if (mr.prog2 != null && mr.inn2BatTeamId != null) {
                if (mr.inn2BatTeamId.equals(teamA.getId())) {
                    for (int i = 0; i < milestones.length; i++) progA2[i] += mr.prog2[i];
                } else {
                    for (int i = 0; i < milestones.length; i++) progB2[i] += mr.prog2[i];
                }
            }
        }

        // Build result JSON
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("totalMatches", numMatches);
        resultJson.put("teamAName", config.get("teamAName"));
        resultJson.put("teamBName", config.get("teamBName"));
        resultJson.put("teamAWins", teamAWins);
        resultJson.put("teamBWins", teamBWins);
        resultJson.put("draws", draws);
        resultJson.put("battingFirstWins", batFirstWins);
        resultJson.put("battingSecondWins", batSecondWins);
        resultJson.put("avgFirstInningsRuns", numMatches > 0 ? Math.round((double) totalInn1Runs / numMatches * 10) / 10.0 : 0);
        resultJson.put("avgFirstInningsWickets", numMatches > 0 ? Math.round((double) totalInn1Wkts / numMatches * 10) / 10.0 : 0);
        resultJson.put("avgSecondInningsRuns", numMatches > 0 ? Math.round((double) totalInn2Runs / numMatches * 10) / 10.0 : 0);
        resultJson.put("avgSecondInningsWickets", numMatches > 0 ? Math.round((double) totalInn2Wkts / numMatches * 10) / 10.0 : 0);

        // Score distribution
        String[] bucketNames = {"under100", "100to150", "150to200", "200to250", "250to300", "300to350", "above350"};
        Map<String, Object> dist = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            Map<String, Integer> b = new LinkedHashMap<>();
            b.put("count", distCount[i]);
            b.put("chaseWins", distChaseWins[i]);
            dist.put(bucketNames[i], b);
        }
        resultJson.put("scoreDistribution", dist);

        // Progressive scores
        Map<String, Object> progressive = new LinkedHashMap<>();
        progressive.put("milestones", milestones);
        progressive.put("teamA", avgArray(progA1, progA2, numMatches));
        progressive.put("teamB", avgArray(progB1, progB2, numMatches));
        resultJson.put("progressiveScores", progressive);

        // Batting stats
        resultJson.put("batting", Map.of(
                "teamA", buildBatRows(batMap, playersA),
                "teamB", buildBatRows(batMap, playersB)
        ));
        resultJson.put("bowling", Map.of(
                "teamA", buildBowlRows(bowlMap, playersA),
                "teamB", buildBowlRows(bowlMap, playersB)
        ));
        resultJson.put("fielding", Map.of(
                "teamA", buildFldRows(fldMap, playersA),
                "teamB", buildFldRows(fldMap, playersB)
        ));

        session.setResultJson(objectMapper.writeValueAsString(resultJson));
        session.setStatus("DONE");
        simSessionRepository.save(session);
    }

    // ─────────────────────────────────────────────────────────────────
    // Transactional match-data reader (keeps a session open for lazy loading)
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MatchReadResult readAndAccumulate(UUID fixtureId, int[] milestones,
            Map<UUID, BatStats> batMap, Map<UUID, BowlStats> bowlMap, Map<UUID, FldStats> fldMap) {
        MatchReadResult out = new MatchReadResult();
        Optional<MatchResult> opt = matchResultRepository.findByFixtureIdWithInnings(fixtureId);
        if (opt.isEmpty()) return out;
        MatchResult result = opt.get();

        List<Innings> innings = result.getInningsList();
        if (innings == null || innings.isEmpty()) return out;
        out.hasInnings = true;

        if (result.getWinner() != null) out.winnerId = result.getWinner().getId();

        Innings inn1 = innings.get(0);
        Innings inn2 = innings.size() > 1 ? innings.get(1) : null;

        out.inn1BatTeamId = inn1.getBattingTeam().getId();
        out.inn1Runs = inn1.getTotalRuns();
        out.inn1Wkts = inn1.getTotalWickets();
        if (inn2 != null) {
            out.inn2BatTeamId = inn2.getBattingTeam().getId();
            out.inn2Runs = inn2.getTotalRuns();
            out.inn2Wkts = inn2.getTotalWickets();
        }

        out.prog1 = buildProgressiveScores(inn1, milestones);
        out.prog2 = inn2 != null ? buildProgressiveScores(inn2, milestones) : new long[milestones.length];

        for (Innings inn : innings) {
            for (BattingScorecard bs : inn.getBattingCards()) {
                UUID pid = bs.getPlayer().getId();
                BatStats bat = batMap.get(pid);
                if (bat != null) bat.add(bs);
            }
            for (BowlingScorecard bsc : inn.getBowlingCards()) {
                UUID pid = bsc.getPlayer().getId();
                BowlStats bowl = bowlMap.get(pid);
                if (bowl != null) bowl.add(bsc);
            }
            for (BattingScorecard bs : inn.getBattingCards()) {
                if (bs.getFielder() != null) {
                    UUID fid = bs.getFielder().getId();
                    FldStats fld = fldMap.get(fid);
                    if (fld != null) {
                        String dt = bs.getDismissalType();
                        if ("caught".equalsIgnoreCase(dt)) fld.catches++;
                        else if ("stumped".equalsIgnoreCase(dt)) fld.stumpings++;
                        else if ("run_out".equalsIgnoreCase(dt)) fld.runOuts++;
                    }
                }
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────
    // Entity creation helpers
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    protected Team createSimTeam(String name, String country, UUID sessionId) {
        Team t = Team.builder()
                .teamName(name)
                .country(country)
                .simSessionId(sessionId)
                .isBot(true)
                .build();
        return teamRepository.save(t);
    }

    @Transactional
    protected List<Player> createSimPlayers(List<Map<String, Object>> cfgs, Team team, UUID sessionId) {
        // Use a per-player random suffix so (firstName, lastName) is globally unique
        // across all sim sessions and both teams within the same session.
        List<Player> players = new ArrayList<>();
        for (Map<String, Object> cfg : cfgs) {
            String fullName = (String) cfg.getOrDefault("name", "Player");
            String[] parts = fullName.split(" ", 2);
            String firstName = parts[0];
            String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            String lastName = (parts.length > 1 ? parts[1] : "Sim") + "_" + uniqueSuffix;

            String bowlType = (String) cfg.get("bowlType");
            String bowlHand = (String) cfg.get("bowlHand");

            Player p = Player.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .country(team.getCountry())
                    .team(team)
                    .role((String) cfg.getOrDefault("role", "BATSMAN"))
                    .age(25)
                    .ageDays(0)
                    .batHand((String) cfg.getOrDefault("batHand", "RH"))
                    .bowlHand(bowlHand)
                    .bowlType(bowlType)
                    .batRating(intVal(cfg, "batRating", 50))
                    .bowlRating(intVal(cfg, "bowlRating", 20))
                    .keeperRating(intVal(cfg, "keeperRating", 0))
                    .fldRating(intVal(cfg, "fldRating", 50))
                    .experience(30)
                    .stamina(intVal(cfg, "stamina", 70))
                    .confidence(intVal(cfg, "confidence", 50))
                    .batAggression((String) cfg.getOrDefault("batAggression", "N"))
                    .bowlAggression((String) cfg.getOrDefault("bowlAggression", "N"))
                    .simSessionId(sessionId)
                    .build();
            players.add(playerRepository.save(p));
        }
        return players;
    }

    @Transactional
    protected UUID createSimFixture(Team teamA, Team teamB, String format, String pitchType, UUID sessionId) {
        Fixture f = Fixture.builder()
                .homeTeam(teamA)
                .awayTeam(teamB)
                .format(format.toUpperCase())
                .matchType("SIM")
                .status("SCHEDULED")
                .pitchType(pitchType)
                .matchDate(LocalDate.now())
                .simSessionId(sessionId)
                .build();
        return fixtureRepository.save(f).getId();
    }

    @Transactional
    protected void createSimLineup(UUID fixtureId, Team teamA, Team teamB,
                                   List<Player> playersA, List<Player> playersB,
                                   List<Map<String, Object>> cfgA, List<Map<String, Object>> cfgB,
                                   int maxOvers) {
        Fixture fixture = fixtureRepository.findById(fixtureId).orElseThrow();

        MatchLineup lineupA = buildLineup(fixture, teamA, playersA, cfgA, maxOvers);
        MatchLineup lineupB = buildLineup(fixture, teamB, playersB, cfgB, maxOvers);

        matchLineupRepository.save(lineupA);
        matchLineupRepository.save(lineupB);
    }

    private MatchLineup buildLineup(Fixture fixture, Team team, List<Player> players,
                                    List<Map<String, Object>> cfgs, int maxOvers) {
        // Find keeper and captain (first KEEPER role or first player)
        Player keeper = players.stream()
                .filter(p -> "KEEPER".equalsIgnoreCase(p.getRole()))
                .findFirst().orElse(players.get(0));
        Player captain = players.get(0);

        MatchLineup lineup = MatchLineup.builder()
                .fixture(fixture)
                .team(team)
                .captain(captain)
                .keeper(keeper)
                .bowlingPlan("BALANCED")
                .build();

        // Lineup players
        for (int i = 0; i < players.size(); i++) {
            Map<String, Object> cfg = cfgs.get(i);
            int batPos = intVal(cfg, "battingPosition", i + 1);
            String batAgg = (String) cfg.getOrDefault("batAggression", "N");
            LineupPlayer lp = LineupPlayer.builder()
                    .lineup(lineup)
                    .player(players.get(i))
                    .battingPosition(batPos)
                    .batAggression(batAgg)
                    .build();
            lineup.getPlayers().add(lp);
        }

        // Bowling order: assign overs based on bowlOvers quota
        List<int[]> bowlQuota = new ArrayList<>(); // [playerIndex, remainingOvers]
        for (int i = 0; i < cfgs.size(); i++) {
            int overs = intVal(cfgs.get(i), "bowlOvers", 0);
            if (overs > 0) {
                bowlQuota.add(new int[]{i, overs});
            }
        }
        // If no quotas defined, distribute evenly among bowlers
        if (bowlQuota.isEmpty()) {
            for (int i = 0; i < cfgs.size(); i++) {
                String role = (String) cfgs.get(i).getOrDefault("role", "BATSMAN");
                if (role.contains("BOWL") || role.contains("ALL")) {
                    bowlQuota.add(new int[]{i, maxOvers / Math.max(1, 5)});
                }
            }
            // Fallback: all players bowl equally
            if (bowlQuota.isEmpty()) {
                for (int i = 0; i < cfgs.size() && i < 5; i++) {
                    bowlQuota.add(new int[]{i, maxOvers / 5});
                }
            }
        }

        int prevBowlerIdx = -1;
        for (int over = 1; over <= maxOvers; over++) {
            int chosen = -1;
            // Pick first available bowler with overs remaining who isn't prevBowler
            for (int[] quota : bowlQuota) {
                if (quota[1] > 0 && quota[0] != prevBowlerIdx) {
                    chosen = quota[0];
                    quota[1]--;
                    break;
                }
            }
            if (chosen == -1) {
                // Relax constraint (consecutive allowed)
                for (int[] quota : bowlQuota) {
                    if (quota[1] > 0) {
                        chosen = quota[0];
                        quota[1]--;
                        break;
                    }
                }
            }
            if (chosen == -1) chosen = 0; // absolute fallback

            Player bowler = players.get(Math.min(chosen, players.size() - 1));
            String bowlAgg = (String) cfgs.get(Math.min(chosen, cfgs.size() - 1)).getOrDefault("bowlAggression", "N");
            BowlingOrder bo = BowlingOrder.builder()
                    .lineup(lineup)
                    .overNumber(over)
                    .bowler(bowler)
                    .aggression(bowlAgg)
                    .build();
            lineup.getBowlingOrders().add(bo);
            prevBowlerIdx = chosen;
        }

        return lineup;
    }

    // ─────────────────────────────────────────────────────────────────
    // Stats helpers
    // ─────────────────────────────────────────────────────────────────

    private long[] buildProgressiveScores(Innings inn, int[] milestones) {
        long[] prog = new long[milestones.length];
        List<BallEvent> balls = ballEventRepository
                .findByInningsIdOrderByOverNumberAscBallNumberAsc(inn.getId());
        // Accumulate legal ball count and runs, snapshot at each milestone
        int legalBalls = 0;
        int cumRuns = 0;
        int mIdx = 0;
        for (BallEvent be : balls) {
            boolean legal = !Boolean.TRUE.equals(be.getIsWide()) && !Boolean.TRUE.equals(be.getIsNoBall());
            cumRuns += be.getRuns();
            if (legal) {
                legalBalls++;
                int oversCompleted = legalBalls / 6;
                while (mIdx < milestones.length && oversCompleted >= milestones[mIdx]) {
                    prog[mIdx] = cumRuns;
                    mIdx++;
                }
            }
        }
        // Fill remaining milestones with final score
        while (mIdx < milestones.length) {
            prog[mIdx] = inn.getTotalRuns();
            mIdx++;
        }
        return prog;
    }

    private int scoreBucket(int runs) {
        if (runs < 100) return 0;
        if (runs < 150) return 1;
        if (runs < 200) return 2;
        if (runs < 250) return 3;
        if (runs < 300) return 4;
        if (runs < 350) return 5;
        return 6;
    }

    private Map<String, Object> avgArray(long[] arr1, long[] arr2, int n) {
        // Returns avg first innings prog + avg second innings prog
        Map<String, Object> result = new LinkedHashMap<>();
        double[] avg1 = new double[arr1.length];
        double[] avg2 = new double[arr2.length];
        for (int i = 0; i < arr1.length; i++) avg1[i] = n > 0 ? Math.round((double) arr1[i] / n * 10) / 10.0 : 0;
        for (int i = 0; i < arr2.length; i++) avg2[i] = n > 0 ? Math.round((double) arr2[i] / n * 10) / 10.0 : 0;
        result.put("innings1", avg1);
        result.put("innings2", avg2);
        return result;
    }

    private List<Map<String, Object>> buildBatRows(Map<UUID, BatStats> batMap, List<Player> players) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Player p : players) {
            BatStats s = batMap.get(p.getId());
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.getFirstName() + " " + p.getLastName());
            row.put("innings", s.innings);
            row.put("runs", s.runs);
            row.put("balls", s.balls);
            row.put("avg", s.innings > 0 ? Math.round((double) s.runs / s.innings * 100) / 100.0 : 0);
            row.put("sr", s.balls > 0 ? Math.round((double) s.runs / s.balls * 10000) / 100.0 : 0);
            row.put("fours", s.fours);
            row.put("sixes", s.sixes);
            row.put("fifties", s.fifties);
            row.put("hundreds", s.hundreds);
            row.put("highest", s.highest);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildBowlRows(Map<UUID, BowlStats> bowlMap, List<Player> players) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Player p : players) {
            BowlStats s = bowlMap.get(p.getId());
            if (s == null) continue;
            if (s.innings == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.getFirstName() + " " + p.getLastName());
            row.put("innings", s.innings);
            row.put("overs", Math.round(s.overs * 10) / 10.0);
            row.put("maidens", s.maidens);
            row.put("runs", s.runs);
            row.put("wickets", s.wickets);
            row.put("avg", s.wickets > 0 ? Math.round((double) s.runs / s.wickets * 100) / 100.0 : 0);
            row.put("econ", s.overs > 0 ? Math.round(s.runs / s.overs * 100) / 100.0 : 0);
            row.put("sr", s.wickets > 0 ? Math.round(s.overs * 6 / s.wickets * 10) / 10.0 : 0);
            row.put("threeWickets", s.threeWickets);
            row.put("fiveWickets", s.fiveWickets);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildFldRows(Map<UUID, FldStats> fldMap, List<Player> players) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Player p : players) {
            FldStats s = fldMap.get(p.getId());
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.getFirstName() + " " + p.getLastName());
            row.put("catches", s.catches);
            row.put("stumpings", s.stumpings);
            row.put("runOuts", s.runOuts);
            rows.add(row);
        }
        return rows;
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────

    private int intVal(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    // ─────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────

    static class MatchReadResult {
        boolean hasInnings;
        UUID winnerId;
        UUID inn1BatTeamId;
        UUID inn2BatTeamId;
        int inn1Runs, inn1Wkts, inn2Runs, inn2Wkts;
        long[] prog1;
        long[] prog2;
    }

    private static class BatStats {
        final Player player;
        int innings, runs, balls, fours, sixes, fifties, hundreds, highest;
        BatStats(Player p) { this.player = p; }
        void add(BattingScorecard bs) {
            innings++;
            runs += bs.getRunsScored();
            balls += bs.getBallsFaced();
            fours += bs.getFours();
            sixes += bs.getSixes();
            if (bs.getRunsScored() >= 100) hundreds++;
            else if (bs.getRunsScored() >= 50) fifties++;
            if (bs.getRunsScored() > highest) highest = bs.getRunsScored();
        }
    }

    private static class BowlStats {
        final Player player;
        int innings, maidens, runs, wickets, threeWickets, fiveWickets;
        double overs;
        BowlStats(Player p) { this.player = p; }
        void add(BowlingScorecard bsc) {
            if (bsc.getOvers() == null || bsc.getOvers() == 0.0) return;
            innings++;
            overs += bsc.getOvers();
            maidens += bsc.getMaidens();
            runs += bsc.getRunsConceded();
            wickets += bsc.getWickets();
            if (bsc.getWickets() >= 5) fiveWickets++;
            else if (bsc.getWickets() >= 3) threeWickets++;
        }
    }

    private static class FldStats {
        final Player player;
        int catches, stumpings, runOuts;
        FldStats(Player p) { this.player = p; }
    }
}
