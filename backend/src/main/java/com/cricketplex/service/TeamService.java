package com.cricketplex.service;

import com.cricketplex.dto.TeamSetupRequest;
import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final int SQUAD_SIZE = 16;
    private static final String[] AGGRESSIONS = {"D", "N", "A"};
    private static final String[] PART_TIME_BOWL_TYPES = {"M", "MF", "FS"};

    /*  Bowl-type slots for the 8 bowler/AR positions:
        2 FS (Finger Spin), 1 WS (Wrist Spin), 1 F (Fast),
        1 M (Medium), 2 FM (Fast-Medium), 1 MF (Medium-Fast)   */
    private static final String[] BOWL_TYPE_SLOTS =
            {"FS", "FS", "WS", "F", "M", "FM", "FM", "MF"};

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PlayerFirstNameRepository firstNameRepo;
    private final PlayerLastNameRepository lastNameRepo;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureService fixtureService;
    private final CupService cupService;

    @Transactional
    public Team setupTeam(User owner, TeamSetupRequest request) {
        if (teamRepository.existsByOwner(owner)) {
            throw new IllegalArgumentException("You already have a team");
        }
        if (teamRepository.existsByTeamName(request.getTeamName())) {
            throw new IllegalArgumentException("Team name is already taken");
        }

        Team team = Team.builder()
                .teamName(request.getTeamName())
                .country(request.getCountry())
                .owner(owner)
                .build();
        team = teamRepository.save(team);

        // Generate 16 players for this team
        generateSquadForTeam(team);

        // Assign team to leagues (one per format: T20, ODI, FC)
        assignTeamToLeagues(team);


        owner.setTeamSetupDone(true);
        userRepository.save(owner);
        return team;
    }

    /**
     * Generate 16 players for any team (user or bot).
     * Composition: 6 batsmen (4 RH + 2 LH), 2 keepers (1 RH + 1 LH),
     * 4 bowlers + 4 all-rounders with specific bowl-type mix.
     */
    public void generateSquadForTeam(Team team) {
        // Skip if team already has players
        List<Player> existing = playerRepository.findByTeam(team);
        if (!existing.isEmpty()) return;

        // Fetch name pool from ALL countries
        List<PlayerFirstName> poolFirst = firstNameRepo.findAll();
        List<PlayerLastName> poolLast = lastNameRepo.findAll();

        if (poolFirst.isEmpty() || poolLast.isEmpty()) {
            throw new IllegalArgumentException("No player names available. Admin must add name pools first.");
        }

        Set<String> globalUsed = playerRepository.findAllNameCombos();
        Set<String> localUsed = new HashSet<>();
        Random rng = new Random();
        List<Player> squad = new ArrayList<>(SQUAD_SIZE);

        generateBatsmen(squad, team, poolFirst, poolLast, rng, globalUsed, localUsed);
        generateKeepers(squad, team, poolFirst, poolLast, rng, globalUsed, localUsed);
        generateBowlersAndArs(squad, team, poolFirst, poolLast, rng, globalUsed, localUsed);

        if (!squad.isEmpty()) {
            playerRepository.saveAll(squad);
        }
    }

    /**
     * Check whether a country has available bot slots in the lowest division
     * for all 3 formats (T20, ODI, FC).
     * Returns true only if every format has at least one bot to replace.
     */
    public boolean isCountryAvailable(String country) {
        String[] formats = {"T20", "ODI", "FC"};
        for (String format : formats) {
            if (!hasAvailableBotSlot(country, format)) return false;
        }
        return true;
    }

    private boolean hasAvailableBotSlot(String country, String format) {
        return countBotSlots(country, format) > 0;
    }

    private int countBotSlots(String country, String format) {
        Integer maxDiv = leagueRepository.findMaxDivisionForFormat(country, format);
        if (maxDiv == null) return 0;
        int currentSeason = leagueRepository.findMaxSeason();

        List<League> bottomLeagues = leagueRepository
                .findByCountryIgnoreCaseAndFormatAndSeasonOrderByDivisionAscLeagueNumberAsc(
                        country, format, currentSeason);

        int count = 0;
        for (League l : bottomLeagues) {
            if (!l.getDivision().equals(maxDiv)) continue;
            List<LeagueTeam> entries = leagueTeamRepository.findByLeagueIdAndSeason(l.getId(), currentSeason);
            for (LeagueTeam lt : entries) {
                if (Boolean.TRUE.equals(lt.getTeam().getIsBot())) count++;
            }
        }
        return count;
    }

    /**
     * Count available slots for a country.
     * Bottleneck is the format with the fewest bot slots.
     */
    public int countAvailableSlots(String country) {
        String[] formats = {"T20", "ODI", "FC"};
        int min = Integer.MAX_VALUE;
        for (String format : formats) {
            min = Math.min(min, countBotSlots(country, format));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * Assign a new team to the bottom division for each format,
     * replacing one bot team in each league.
     * Throws if any format's lowest division has no bot slot available.
     */
    @Transactional
    public void assignTeamToLeagues(Team team) {
        String country = team.getCountry();
        String[] formats = {"T20", "ODI", "FC"};

        // ── Pre-check: ensure every format has a bot slot ──
        for (String format : formats) {
            if (!hasAvailableBotSlot(country, format)) {
                throw new IllegalArgumentException(
                        "Leagues are full for " + country + ". No available slot in the lowest " + format + " division. Please choose a different country.");
            }
        }

        for (String format : formats) {
            // Find the highest division (bottom of the hierarchy)
            Integer maxDiv = leagueRepository.findMaxDivisionForFormat(country, format);
            if (maxDiv == null) continue;
            int currentSeason = leagueRepository.findMaxSeason();

            // Get all leagues in the bottom division for this format
            List<League> bottomLeagues = leagueRepository
                    .findByCountryIgnoreCaseAndFormatAndSeasonOrderByDivisionAscLeagueNumberAsc(
                            country, format, currentSeason);

            // Collect only bottom-division leagues that have at least one bot
            List<League> candidates = new ArrayList<>();
            for (League l : bottomLeagues) {
                if (!l.getDivision().equals(maxDiv)) continue;
                List<LeagueTeam> entries = leagueTeamRepository.findByLeagueIdAndSeason(l.getId(), currentSeason);
                boolean hasBot = entries.stream().anyMatch(lt -> Boolean.TRUE.equals(lt.getTeam().getIsBot()));
                if (hasBot) candidates.add(l);
            }
            if (candidates.isEmpty()) continue;

            // Pick a random league among those with bots
            League targetLeague = candidates.get(new Random().nextInt(candidates.size()));

            // Ensure fixtures exist (lazy generation for pre-V13 leagues)
            fixtureService.ensureFixturesExist(targetLeague);

            // Find a bot team in this league to replace — prefer one not in an active match
            List<LeagueTeam> entries = leagueTeamRepository.findByLeagueIdAndSeason(targetLeague.getId(), currentSeason);
            LeagueTeam botEntry = null;
            for (LeagueTeam lt : entries) {
                if (Boolean.TRUE.equals(lt.getTeam().getIsBot())) {
                    if (!fixtureService.hasActiveMatch(targetLeague.getId(), lt.getTeam().getId())) {
                        botEntry = lt;
                        break;  // ideal: bot with no active match
                    }
                    if (botEntry == null) {
                        botEntry = lt;  // fallback: bot with active match (deferred swap will handle it)
                    }
                }
            }

            if (botEntry != null) {
                UUID oldBotId = botEntry.getTeam().getId();
                botEntry.setTeam(team);
                botEntry.setSeason(currentSeason);
                leagueTeamRepository.save(botEntry);
                fixtureService.swapTeamInFixtures(targetLeague.getId(), oldBotId, team.getId());
            }
        }

        // Replace a bot slot in the current season's Cup (if one exists)
        try {
            cupService.replaceBotWithHuman(team);
        } catch (Exception e) {
            // Non-critical – cup replacement failure must not block team setup
        }
    }

    // ────────────────── Batsmen ──────────────────

    private void generateBatsmen(List<Player> squad, Team team,
                                 List<PlayerFirstName> poolFirst, List<PlayerLastName> poolLast,
                                 Random rng, Set<String> globalUsed, Set<String> localUsed) {
        // 4 RH + 2 LH
        String[] hands = {"RH", "RH", "RH", "RH", "LH", "LH"};
        for (String batHand : hands) {
            String[] name = pickUniqueName(poolFirst, poolLast, rng, globalUsed, localUsed);
            if (name == null) continue;
            int batR = randInt(rng, 25, 35);
            int bowlR = randInt(rng, 0, 15);
            int fldR = randInt(rng, 10, 35);
                squad.add(Player.builder()
                    .firstName(name[0]).lastName(name[1]).country(name[2]).team(team)
                    .nationality(name[2])
                    .role("BATSMAN").age(randInt(rng, 18, 35)).ageDays(randInt(rng, 0, 55)).batHand(batHand)
                    .bowlHand(batHand).bowlType(randomPartTimeBowlType(rng))
                    .batRating(batR).bowlRating(bowlR)
                    .keeperRating(randInt(rng, 5, 12)).fldRating(fldR)
                    .rating(calcOverallRating("BATSMAN", batR, bowlR, 0, fldR))
                    .wage(randInt(rng, 300, 800))
                    .confidence(randInt(rng, 40, 65))
                    .experience(randInt(rng, 5, 15)).stamina(randInt(rng, 10, 25)).fitness(100)
                    .batAggression(randomAggression(rng)).bowlAggression(randomAggression(rng))
                    .build());
        }
    }

    // ────────────────── Keepers ──────────────────

    private void generateKeepers(List<Player> squad, Team team,
                                 List<PlayerFirstName> poolFirst, List<PlayerLastName> poolLast,
                                 Random rng, Set<String> globalUsed, Set<String> localUsed) {
        // 1 RH + 1 LH
        String[] hands = {"RH", "LH"};
        for (String batHand : hands) {
            String[] name = pickUniqueName(poolFirst, poolLast, rng, globalUsed, localUsed);
            if (name == null) continue;
            int batR = randInt(rng, 25, 33);
            int bowlR = randInt(rng, 0, 5);
            int kpR = randInt(rng, 20, 30);
            int fldR = randInt(rng, 10, 35);
            squad.add(Player.builder()
                    .firstName(name[0]).lastName(name[1]).country(name[2]).team(team)
                    .nationality(name[2])
                    .role("KEEPER").age(randInt(rng, 18, 35)).ageDays(randInt(rng, 0, 55)).batHand(batHand)
                    .bowlHand(batHand).bowlType(randomPartTimeBowlType(rng))
                    .batRating(batR).bowlRating(bowlR)
                    .keeperRating(kpR).fldRating(fldR)
                    .rating(calcOverallRating("KEEPER", batR, bowlR, kpR, fldR))
                    .wage(randInt(rng, 300, 800))
                    .confidence(randInt(rng, 40, 65))
                    .experience(randInt(rng, 5, 15)).stamina(randInt(rng, 10, 25)).fitness(100)
                    .batAggression(randomAggression(rng)).bowlAggression(randomAggression(rng))
                    .build());
        }
    }

    // ────────────────── Bowlers + All-Rounders ──────────────────

    private void generateBowlersAndArs(List<Player> squad, Team team,
                                       List<PlayerFirstName> poolFirst, List<PlayerLastName> poolLast,
                                       Random rng, Set<String> globalUsed, Set<String> localUsed) {

        // Shuffle bowl-type slots
        List<String> types = new ArrayList<>(Arrays.asList(BOWL_TYPE_SLOTS));
        Collections.shuffle(types, rng);

        // First 4 → BOWLER, next 4 → ALL_ROUNDER
        String[] roles = {"BOWLER", "BOWLER", "BOWLER", "BOWLER",
                          "ALL_ROUNDER", "ALL_ROUNDER", "ALL_ROUNDER", "ALL_ROUNDER"};

        // Pick 2 random indices (out of 8) for left-handed bowlers
        Set<Integer> lhIndices = new HashSet<>();
        while (lhIndices.size() < 2) {
            lhIndices.add(rng.nextInt(8));
        }

        for (int i = 0; i < 8; i++) {
            String[] name = pickUniqueName(poolFirst, poolLast, rng, globalUsed, localUsed);
            if (name == null) continue;

            String role = roles[i];
            String bowlType = types.get(i);
            String bowlHand = lhIndices.contains(i) ? "LH" : "RH";
            String batHand = rng.nextBoolean() ? "RH" : "LH";

            int batRat, bowlRat;
            if ("BOWLER".equals(role)) {
                batRat = randInt(rng, 0, 15);
                bowlRat = randInt(rng, 25, 35);
            } else {
                batRat = randInt(rng, 20, 30);
                bowlRat = randInt(rng, 20, 30);
            }

                squad.add(Player.builder()
                    .firstName(name[0]).lastName(name[1]).country(name[2]).team(team)
                    .nationality(name[2])
                    .role(role).age(randInt(rng, 18, 35)).ageDays(randInt(rng, 0, 55)).batHand(batHand)
                    .bowlHand(bowlHand).bowlType(bowlType)
                    .batRating(batRat).bowlRating(bowlRat)
                    .keeperRating(randInt(rng, 5, 12)).fldRating(randInt(rng, 10, 35))
                    .rating(calcOverallRating(role, batRat, bowlRat, 0, randInt(rng, 10, 35)))
                    .wage(randInt(rng, 300, 800))
                    .confidence(randInt(rng, 40, 65))
                    .experience(randInt(rng, 5, 15)).stamina(randInt(rng, 10, 25)).fitness(100)
                    .batAggression(randomAggression(rng)).bowlAggression(randomAggression(rng))
                    .build());
        }
    }

    // ────────────────── Helpers ──────────────────

    /**
     * Picks a first+last name combo that is unique both globally (across all teams)
     * and locally (within this squad). Returns null if exhausted after many retries.
     */
    private String[] pickUniqueName(List<PlayerFirstName> poolFirst,
                                    List<PlayerLastName> poolLast,
                                    Random rng,
                                    Set<String> globalUsed,
                                    Set<String> localUsed) {
        for (int attempt = 0; attempt < 50; attempt++) {
            PlayerFirstName first = poolFirst.get(rng.nextInt(poolFirst.size()));
            String fn = first.getName();
            String ln = poolLast.get(rng.nextInt(poolLast.size())).getName();
            String key = fn.toLowerCase() + "|" + ln.toLowerCase();
            if (!globalUsed.contains(key) && !localUsed.contains(key)) {
                localUsed.add(key);
                return new String[]{fn, ln, first.getCountry()};
            }
        }
        return null;
    }

    private int randInt(Random rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    private String randomAggression(Random rng) {
        return AGGRESSIONS[rng.nextInt(AGGRESSIONS.length)];
    }

    private String randomPartTimeBowlType(Random rng) {
        return PART_TIME_BOWL_TYPES[rng.nextInt(PART_TIME_BOWL_TYPES.length)];
    }

    private int calcOverallRating(String role, int bat, int bowl, int keeper, int fld) {
        return switch (role) {
            case "BATSMAN"     -> (int)(bat * 0.55 + bowl * 0.10 + fld * 0.35);
            case "BOWLER"      -> (int)(bat * 0.10 + bowl * 0.55 + fld * 0.35);
            case "ALL_ROUNDER" -> (int)(bat * 0.35 + bowl * 0.35 + fld * 0.30);
            case "KEEPER"      -> (int)(bat * 0.30 + bowl * 0.05 + keeper * 0.35 + fld * 0.30);
            default            -> (bat + bowl + fld) / 3;
        };
    }

    /**
     * Admin method: Assign squad to a team by team ID.
     * Used when a team doesn't have players assigned.
     */
    @Transactional
    public void assignSquadToTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        generateSquadForTeam(team);
    }
}
