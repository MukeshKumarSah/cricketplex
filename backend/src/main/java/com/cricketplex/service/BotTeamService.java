package com.cricketplex.service;

import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Team;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotTeamService {

    private static final int TEAMS_PER_LEAGUE = 8;

    private static final String[] SUFFIXES = {
            "Strikers", "Warriors", "Kings", "Titans",
            "Royals", "Challengers", "Riders", "Giants",
            "Lions", "Eagles", "Thunder", "Knights",
            "Wolves", "Scorchers", "Chargers", "Gladiators",
            "Hurricanes", "Blazers", "Panthers", "Falcons",
            "Stallions", "Spartans", "Raptors", "Vikings"
    };

    // League structure: Div 1 → 1 league, Div 2 → 2 leagues = 3 leagues × 8 teams = 24 per country
    private static final int[][] LEAGUE_SLOTS = {
            {1, 1}, // Div 1 League 1
            {2, 1}, // Div 2 League 1
            {2, 2}, // Div 2 League 2
    };

    private static final Map<String, List<String>> CITIES = new LinkedHashMap<>();

    static {
        CITIES.put("Afghanistan", List.of(
                "Kabul", "Kandahar", "Herat", "Mazar-i-Sharif", "Jalalabad", "Kunduz", "Ghazni", "Balkh",
                "Baghlan", "Khost", "Gardez", "Farah", "Taloqan", "Lashkargah", "Charikar", "Sheberghan",
                "Zaranj", "Faizabad", "Maymana", "Bamyan", "Aybak", "Mehtar Lam", "Pul-i-Alam", "Nili"));
        CITIES.put("Australia", List.of(
                "Sydney", "Melbourne", "Brisbane", "Perth", "Adelaide", "Canberra", "Hobart", "Darwin",
                "Gold Coast", "Newcastle", "Wollongong", "Geelong", "Cairns", "Townsville", "Ballarat", "Bendigo",
                "Toowoomba", "Launceston", "Mackay", "Rockhampton", "Bunbury", "Wagga Wagga", "Albury", "Mandurah"));
        CITIES.put("Bangladesh", List.of(
                "Dhaka", "Chittagong", "Khulna", "Rajshahi", "Sylhet", "Rangpur", "Barisal", "Comilla",
                "Gazipur", "Narayanganj", "Mymensingh", "Bogra", "Jessore", "Dinajpur", "Brahmanbaria", "Tangail",
                "Narsingdi", "Savar", "Nawabganj", "Faridpur", "Kushtia", "Pabna", "Tongi", "Chandpur"));
        CITIES.put("England", List.of(
                "London", "Manchester", "Birmingham", "Liverpool", "Leeds", "Sheffield", "Bristol", "Nottingham",
                "Leicester", "Southampton", "Newcastle", "Brighton", "Norwich", "Oxford", "Cambridge", "Bath",
                "York", "Exeter", "Derby", "Reading", "Chester", "Canterbury", "Durham", "Worcester"));
        CITIES.put("India", List.of(
                "Mumbai", "Delhi", "Bangalore", "Chennai", "Kolkata", "Hyderabad", "Ahmedabad", "Pune",
                "Jaipur", "Lucknow", "Chandigarh", "Indore", "Guwahati", "Thiruvananthapuram", "Visakhapatnam", "Ranchi",
                "Dharamsala", "Mohali", "Nagpur", "Rajkot", "Cuttack", "Raipur", "Vadodara", "Dehradun"));
        CITIES.put("Ireland", List.of(
                "Dublin", "Cork", "Belfast", "Galway", "Limerick", "Waterford", "Kilkenny", "Drogheda",
                "Dundalk", "Sligo", "Clonmel", "Tralee", "Ennis", "Carlow", "Wexford", "Athlone",
                "Tullamore", "Letterkenny", "Mullingar", "Longford", "Navan", "Newbridge", "Naas", "Arklow"));
        CITIES.put("Nepal", List.of(
                "Kathmandu", "Pokhara", "Biratnagar", "Lalitpur", "Bharatpur", "Birgunj", "Dharan", "Butwal",
                "Hetauda", "Janakpur", "Nepalgunj", "Tulsipur", "Itahari", "Damak", "Ghorahi", "Bhaktapur",
                "Kirtipur", "Tansen", "Birendranagar", "Dhulikhel", "Ilam", "Dhangadhi", "Banepa", "Lumbini"));
        CITIES.put("Netherlands", List.of(
                "Amsterdam", "Rotterdam", "The Hague", "Utrecht", "Eindhoven", "Groningen", "Tilburg", "Almere",
                "Breda", "Nijmegen", "Haarlem", "Arnhem", "Enschede", "Apeldoorn", "Amersfoort", "Delft",
                "Leiden", "Dordrecht", "Zoetermeer", "Maastricht", "Deventer", "Leeuwarden", "Hilversum", "Zaanstad"));
        CITIES.put("New Zealand", List.of(
                "Auckland", "Wellington", "Christchurch", "Hamilton", "Tauranga", "Dunedin", "Napier", "Palmerston North",
                "Nelson", "Rotorua", "New Plymouth", "Whangarei", "Invercargill", "Hastings", "Queenstown", "Timaru",
                "Blenheim", "Gisborne", "Whanganui", "Pukekohe", "Ashburton", "Levin", "Masterton", "Taupo"));
        CITIES.put("Oman", List.of(
                "Muscat", "Salalah", "Sohar", "Nizwa", "Sur", "Ibri", "Barka", "Rustaq",
                "Saham", "Khasab", "Ibra", "Bahla", "Sinaw", "Al Buraimi", "Bidbid", "Al Mudhaibi",
                "Shinas", "Liwa", "Thumrait", "Al Kamil", "Haima", "Adam", "Duqm", "Yanqul"));
        CITIES.put("Pakistan", List.of(
                "Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta",
                "Sialkot", "Gujranwala", "Hyderabad", "Bahawalpur", "Sargodha", "Abbottabad", "Mardan", "Sukkur",
                "Larkana", "Mirpur", "Sahiwal", "Sheikhupura", "Rahim Yar Khan", "Jhang", "Okara", "Gujrat"));
        CITIES.put("Scotland", List.of(
                "Edinburgh", "Glasgow", "Aberdeen", "Dundee", "Inverness", "Perth", "Stirling", "Livingston",
                "Dunfermline", "Kirkcaldy", "Ayr", "Kilmarnock", "Paisley", "Greenock", "Falkirk", "Peterhead",
                "Arbroath", "Elgin", "Dumfries", "St Andrews", "Oban", "Fort William", "Lerwick", "Hawick"));
        CITIES.put("South Africa", List.of(
                "Johannesburg", "Cape Town", "Durban", "Pretoria", "Port Elizabeth", "Bloemfontein", "East London", "Stellenbosch",
                "Pietermaritzburg", "Kimberley", "Polokwane", "Nelspruit", "Rustenburg", "George", "Centurion", "Paarl",
                "Benoni", "Potchefstroom", "Oudtshoorn", "Worcester", "Grahamstown", "Mahikeng", "Mthatha", "Upington"));
        CITIES.put("Sri Lanka", List.of(
                "Colombo", "Kandy", "Galle", "Negombo", "Jaffna", "Batticaloa", "Anuradhapura", "Trincomalee",
                "Matara", "Kurunegala", "Ratnapura", "Badulla", "Dambulla", "Hambantota", "Nuwara Eliya", "Polonnaruwa",
                "Kalutara", "Chilaw", "Mannar", "Vavuniya", "Ampara", "Kegalle", "Moratuwa", "Dehiwala"));
        CITIES.put("United Arab Emirates", List.of(
                "Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Umm Al Quwain", "Al Ain",
                "Khor Fakkan", "Dibba", "Kalba", "Jebel Ali", "Madinat Zayed", "Ruwais", "Hatta", "Masfoot",
                "Dhaid", "Al Madam", "Mleiha", "Falaj Al Mualla", "Masafi", "Al Rams", "Adhen", "Liwa Oasis"));
        CITIES.put("United States", List.of(
                "New York", "Los Angeles", "Chicago", "Houston", "Dallas", "San Francisco", "Atlanta", "Miami",
                "Seattle", "Boston", "Denver", "Philadelphia", "Phoenix", "Portland", "Detroit", "Minneapolis",
                "Austin", "San Diego", "Orlando", "Nashville", "Charlotte", "Tampa", "Indianapolis", "Columbus"));
        CITIES.put("West Indies", List.of(
                "Bridgetown", "Kingston", "Port of Spain", "Georgetown", "St Johns", "Roseau", "Basseterre", "Castries",
                "Kingstown", "St Georges", "Nassau", "Paramaribo", "Scarborough", "Montego Bay", "Spanish Town", "Chaguanas",
                "Linden", "San Fernando", "Ocho Rios", "Falmouth", "Mandeville", "Soufriere", "Plymouth", "Vieux Fort"));
        CITIES.put("Zimbabwe", List.of(
                "Harare", "Bulawayo", "Chitungwiza", "Mutare", "Gweru", "Masvingo", "Kwekwe", "Kadoma",
                "Chinhoyi", "Marondera", "Norton", "Bindura", "Zvishavane", "Chegutu", "Kariba", "Victoria Falls",
                "Hwange", "Beitbridge", "Rusape", "Plumtree", "Karoi", "Chiredzi", "Chipinge", "Shurugwi"));
    }

    private static final String[] FORMATS = {"T20", "ODI", "FC"};

    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final TeamService teamService;
    private final FixtureRepository fixtureRepository;
    private final FixtureService fixtureService;

    /**
     * Generate bot teams for all 18 countries.
     * 3 leagues per country (Div1×1 + Div2×2) × 8 teams = 24 bot teams per country.
     * Each team is assigned to one league per format (same div/league initially).
     * Over time, teams can be promoted/relegated independently per format.
     */
    @Transactional
    public Map<String, Object> generateBotTeams() {
        int created = 0;
        int skipped = 0;
        int assigned = 0;
        List<String> details = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : CITIES.entrySet()) {
            String country = entry.getKey();
            List<String> cities = entry.getValue();

            // Check existing bot teams for this country
            long existingBots = teamRepository.countByCountryIgnoreCaseAndIsBot(country, true);
            if (existingBots >= 24) {
                skipped += 24;
                details.add(country + ": already has " + existingBots + " bot teams, skipped");
                continue;
            }

            // Get all existing team names to avoid duplicates
            Set<String> existingNames = new HashSet<>();
            teamRepository.findAll().forEach(t -> existingNames.add(t.getTeamName().toLowerCase()));

            int cityIdx = (int) existingBots; // continue from where we left off
            int countryCreated = 0;
            List<Team> createdTeams = new ArrayList<>();

            // Create 24 bot teams (8 per league slot)
            for (int[] slot : LEAGUE_SLOTS) {
                int div = slot[0];

                for (int i = 0; i < TEAMS_PER_LEAGUE && cityIdx < cities.size(); i++) {
                    String city = cities.get(cityIdx);
                    String suffix = SUFFIXES[cityIdx % SUFFIXES.length];
                    String teamName = city + " " + suffix;

                    // Ensure unique name
                    if (existingNames.contains(teamName.toLowerCase())) {
                        teamName = city + " CC";
                        if (existingNames.contains(teamName.toLowerCase())) {
                            teamName = city + " XI";
                        }
                    }

                    // Ratings: Div 1 teams are stronger
                    int baseRating = (div == 1) ? 1100 : 900;
                    int variance = 150;
                    Random rng = new Random(teamName.hashCode());

                    Team bot = Team.builder()
                            .teamName(teamName)
                            .country(country)
                            .groundName(city + " Cricket Stadium")
                            .isBot(true)
                            .odiRating(baseRating + rng.nextInt(variance) - variance / 2)
                            .t20Rating(baseRating + rng.nextInt(variance) - variance / 2)
                            .fcRating(baseRating + rng.nextInt(variance) - variance / 2)
                            .fans(div == 1 ? 5000 + rng.nextInt(5000) : 1000 + rng.nextInt(3000))
                            .build();

                    bot = teamRepository.save(bot);
                    try {
                        teamService.generateSquadForTeam(bot);
                    } catch (Exception e) {
                        log.warn("Could not generate squad for bot team {}: {}", bot.getTeamName(), e.getMessage());
                    }
                    existingNames.add(teamName.toLowerCase());
                    createdTeams.add(bot);
                    cityIdx++;
                    created++;
                    countryCreated++;
                }
            }

            // Now assign each team to leagues (one per format)
            // Teams 0-7 → Div 1 League 1, Teams 8-15 → Div 2 League 1, Teams 16-23 → Div 2 League 2
            int teamIdx = 0;
            for (int[] slot : LEAGUE_SLOTS) {
                int div = slot[0];
                int leagueNum = slot[1];

                for (int i = 0; i < TEAMS_PER_LEAGUE && teamIdx < createdTeams.size(); i++, teamIdx++) {
                    Team team = createdTeams.get(teamIdx);

                    // Assign to one league per format (same div/leagueNum initially)
                    for (String format : FORMATS) {
                        List<League> leagues = leagueRepository
                                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, format);

                        League targetLeague = leagues.stream()
                                .filter(l -> l.getDivision() == div && l.getLeagueNumber() == leagueNum)
                                .findFirst()
                                .orElse(null);

                        if (targetLeague != null
                                && !leagueTeamRepository.existsByLeagueIdAndTeamIdAndSeason(targetLeague.getId(), team.getId(), targetLeague.getSeason())) {
                            leagueTeamRepository.save(LeagueTeam.builder()
                                    .league(targetLeague)
                                    .team(team)
                                    .season(targetLeague.getSeason())
                                    .build());
                            assigned++;
                        }
                    }
                }
            }

            details.add(country + ": created " + countryCreated + " bot teams, " + (countryCreated * 3) + " league assignments");
        }

        // Generate fixtures for all leagues that have 8 teams but no fixtures yet
        int fixturesGenerated = 0;
        List<League> allLeagues = leagueRepository.findAll();
        for (League league : allLeagues) {
            long teamCount = leagueTeamRepository.countByLeagueIdAndSeason(league.getId(), league.getSeason());
            if (teamCount >= TEAMS_PER_LEAGUE && fixtureRepository.countByLeagueId(league.getId()) == 0) {
                try {
                    fixtureService.generateFixtures(league);
                    fixturesGenerated++;
                    log.info("Generated fixtures for {} {} {}.{}",
                            league.getCountry(), league.getFormat(),
                            league.getDivision(), league.getLeagueNumber());
                } catch (Exception e) {
                    log.error("Failed to generate fixtures for league {}: {}", league.getId(), e.getMessage());
                }
            }
        }

        return Map.of(
                "created", created,
                "assigned", assigned,
                "skipped", skipped,
                "total", created + skipped,
                "fixturesGenerated", fixturesGenerated,
                "details", details
        );
    }

    /**
     * Fill a newly created league with 8 bot teams.
     * First uses displaced/unassigned bots from the same country,
     * then creates new bots if not enough are available.
     */
    @Transactional
    public void fillLeagueWithBots(League league) {
        String country = league.getCountry();
        String format = league.getFormat();
        int div = league.getDivision();

        long existing = leagueTeamRepository.countByLeagueIdAndSeason(league.getId(), league.getSeason());
        int needed = TEAMS_PER_LEAGUE - (int) existing;
        if (needed <= 0) return;

        // 1. Find displaced bots (in country, not assigned to any league of this format)
        List<Team> unassigned = leagueTeamRepository.findUnassignedBotsByCountryAndFormat(country, format);
        int fromUnassigned = Math.min(unassigned.size(), needed);

        for (int i = 0; i < fromUnassigned; i++) {
            Team bot = unassigned.get(i);
            if (!leagueTeamRepository.existsByLeagueIdAndTeamIdAndSeason(league.getId(), bot.getId(), league.getSeason())) {
                leagueTeamRepository.save(LeagueTeam.builder()
                        .league(league).team(bot).season(league.getSeason()).build());
                // Also assign to the other 2 formats in a matching league
                assignBotToOtherFormats(bot, country, format, div, league.getLeagueNumber());
                needed--;
            }
        }

        // 2. Create new bots if still short
        if (needed > 0) {
            List<String> cities = CITIES.getOrDefault(country, List.of());
            Set<String> existingNames = new HashSet<>();
            teamRepository.findAll().forEach(t -> existingNames.add(t.getTeamName().toLowerCase()));

            Random rng = new Random();
            int baseRating = (div <= 1) ? 1100 : (div == 2) ? 900 : 750;
            int variance = 150;

            for (int i = 0; i < needed; i++) {
                String teamName = generateUniqueBotName(cities, existingNames, rng);
                if (teamName == null) teamName = country + " Bot " + UUID.randomUUID().toString().substring(0, 6);

                Team bot = Team.builder()
                        .teamName(teamName)
                        .country(country)
                        .groundName(teamName.split(" ")[0] + " Cricket Stadium")
                        .isBot(true)
                        .odiRating(baseRating + rng.nextInt(variance) - variance / 2)
                        .t20Rating(baseRating + rng.nextInt(variance) - variance / 2)
                        .fcRating(baseRating + rng.nextInt(variance) - variance / 2)
                        .fans(div <= 1 ? 5000 + rng.nextInt(5000) : 1000 + rng.nextInt(3000))
                        .build();
                bot = teamRepository.save(bot);
                try {
                    teamService.generateSquadForTeam(bot);
                } catch (Exception e) {
                    log.warn("Could not generate squad for bot team {}: {}", bot.getTeamName(), e.getMessage());
                }
                existingNames.add(teamName.toLowerCase());

                // Assign to this league
                leagueTeamRepository.save(LeagueTeam.builder()
                        .league(league).team(bot).season(league.getSeason()).build());

                // Also assign to the other 2 formats in matching leagues
                assignBotToOtherFormats(bot, country, format, div, league.getLeagueNumber());
            }
        }

        log.info("Filled league {} {} {}.{} with {} teams",
                country, format, div, league.getLeagueNumber(), TEAMS_PER_LEAGUE);
    }

    /**
     * When a bot is added to one format's league, also assign it to the
     * same div/leagueNumber in the other two formats (if those leagues exist).
     */
    private void assignBotToOtherFormats(Team bot, String country, String excludeFormat, int div, int leagueNum) {
        for (String fmt : FORMATS) {
            if (fmt.equals(excludeFormat)) continue;

            List<League> leagues = leagueRepository
                    .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, fmt);

            League target = leagues.stream()
                    .filter(l -> l.getDivision() == div && l.getLeagueNumber() == leagueNum)
                    .findFirst().orElse(null);

            if (target != null
                    && !leagueTeamRepository.existsByLeagueIdAndTeamIdAndSeason(target.getId(), bot.getId(), target.getSeason())) {
                leagueTeamRepository.save(LeagueTeam.builder()
                        .league(target).team(bot).season(target.getSeason()).build());
            }
        }
    }

    private String generateUniqueBotName(List<String> cities, Set<String> existingNames, Random rng) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String city = cities.isEmpty() ? "City" + rng.nextInt(999) : cities.get(rng.nextInt(cities.size()));
            String suffix = SUFFIXES[rng.nextInt(SUFFIXES.length)];
            String name = city + " " + suffix;
            if (!existingNames.contains(name.toLowerCase())) return name;
            name = city + " CC";
            if (!existingNames.contains(name.toLowerCase())) return name;
            name = city + " XI";
            if (!existingNames.contains(name.toLowerCase())) return name;
        }
        return null;
    }

    /**
     * Get bot team stats for the admin page.
     */
    public Map<String, Object> getBotStats() {
        List<Team> bots = teamRepository.findByIsBotTrue();
        long total = bots.size();

        Map<String, Long> perCountry = new LinkedHashMap<>();
        for (Team t : bots) {
            perCountry.merge(t.getCountry(), 1L, Long::sum);
        }

        return Map.of(
                "totalBotTeams", total,
                "countriesWithBots", perCountry.size(),
                "perCountry", perCountry
        );
    }
}
