import API from './axios';

export const signup = (data) => API.post('/auth/signup', data);
export const login = (data) => API.post('/auth/login', data);
export const getMe = () => API.get('/auth/me');
export const setupTeam = (data) => API.post('/team/setup', data);
export const checkCountryAvailability = (country) => API.get(`/team/check-availability?country=${encodeURIComponent(country)}`);
export const getAllCountryAvailability = () => API.get('/team/all-country-availability');

// Settings
export const getSettings = () => API.get('/settings');
export const uploadProfilePic = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return API.post('/settings/profile-pic', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const uploadTeamPic = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return API.post('/settings/team-pic', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const updateTeam = (data) => API.put('/settings/team', data);
export const changePassword = (data) => API.put('/settings/password', data);
export const getDashboardStats = () => API.get('/settings/dashboard-stats');
export const getMyTrophies = () => API.get('/settings/trophies');
export const getTeamTrophies = (teamId) => API.get(`/settings/trophies/${teamId}`);
export const updateTheme = (theme) => API.put('/settings/theme', { theme });

// Ground Management
export const getStadiumSeats = () => API.get('/ground/seats');
export const updateStadiumSeats = (data) => API.put('/ground/seats', data);
export const getUpcomingHomeMatches = () => API.get('/ground/matches');
export const updateMatchPitch = (matchId, pitchType) =>
  API.put(`/ground/matches/${matchId}/pitch`, { pitchType });
export const updateDefaultPitch = (pitchType) =>
  API.put('/ground/default-pitch', { pitchType });
export const getAttendanceHistory = () => API.get('/ground/attendance-history');

// Team List
export const getTeamList = () => API.get('/teams');

// Team Profile (public, any team)
export const getTeamProfile = (teamId) => API.get(`/teams/${teamId}`);
export const getTeamSquad = (teamId) => API.get(`/teams/${teamId}/squad`);
export const getTeamMatches = (teamId, season) => API.get(`/teams/${teamId}/matches`, { params: season != null ? { season } : {} });
export const getTeamLeagues = (teamId) => API.get(`/teams/${teamId}/leagues`);
export const getTeamGround = (teamId) => API.get(`/teams/${teamId}/ground`);

// Squad
export const getSquad = () => API.get('/squad');

// Admin - Name Pool Management
export const getPoolStats = () => API.get('/admin/players/stats');
export const getPoolByCountry = (country) => API.get(`/admin/players/pool/${encodeURIComponent(country)}`);
export const addFirstNames = (country, names) => API.post('/admin/players/first-names', { country, names });
export const addLastNames = (country, names) => API.post('/admin/players/last-names', { country, names });
export const deleteFirstName = (id) => API.delete(`/admin/players/first-names/${id}`);
export const deleteLastName = (id) => API.delete(`/admin/players/last-names/${id}`);

// Search
export const searchManagers = (q) => API.get(`/search/managers`, { params: { q } });
export const searchPlayers = (q) => API.get(`/search/players`, { params: { q } });
export const searchTeams = (q) => API.get(`/search/teams`, { params: { q } });
export const searchLeagues = (q) => API.get(`/search/leagues`, { params: { q } });

// Game Info
export const getCurrentSeason = () => API.get('/team/current-season');
export const getMyLeagues = () => API.get('/team/my-leagues');
export const getMyMatches = (season, format) => {
  const params = {};
  if (season) params.season = season;
  if (format) params.format = format;
  return API.get('/team/my-matches', { params });
};

// League Detail
export const getLeagueDetail = (id, season) => API.get(`/leagues/${id}`, { params: season ? { season } : {} });
export const getLeagueFixtures = (id, season) => API.get(`/leagues/${id}/fixtures`, { params: season ? { season } : {} });
export const getLeaguePlayerStats = (id, season) => API.get(`/leagues/${id}/stats`, { params: season ? { season } : {} });
export const getAvailableLeagues = (country, format, season) => {
  const params = {};
  if (format) params.format = format;
  if (season) params.season = season;
  return API.get(`/leagues/available/${encodeURIComponent(country)}`, { params });
};

// Admin - League Management
export const getLeagueStats = () => API.get('/admin/leagues/stats');
export const getLeaguesByCountry = (country, format, season) => {
  const params = {};
  if (format) params.format = format;
  if (season) params.season = season;
  return API.get(`/admin/leagues/${encodeURIComponent(country)}`, { params });
};
export const createLeague = (country, format, division) => API.post('/admin/leagues', { country, format, division });
export const generateBotTeams = () => API.post('/admin/leagues/generate-bots');
export const getBotStats = () => API.get('/admin/leagues/bot-stats');
export const deleteLeague = (id) => API.delete(`/admin/leagues/${id}`);
export const triggerAging = () => API.post('/admin/leagues/trigger-aging');
export const triggerFitness = () => API.post('/admin/leagues/trigger-fitness');
export const triggerTraining = () => API.post('/admin/leagues/trigger-training');
export const triggerSeasonal = (season) => API.post(
  season != null ? `/admin/leagues/trigger-seasonal?season=${season}` : '/admin/leagues/trigger-seasonal'
);
export const devFastForward = (targetWeek) => API.post(`/admin/dev/fast-forward?targetWeek=${targetWeek}`);

// Lineup
export const getLineupData = (fixtureId) => API.get(`/match/${fixtureId}/lineup`);
export const saveLineup = (fixtureId, data) => API.post(`/match/${fixtureId}/lineup`, data);

// FC Strategy
export const getFCState = (fixtureId) => API.get(`/match/fc-state/${fixtureId}`);
export const saveFCStrategy = (fixtureId, data) => API.post(`/match/fc-strategy/${fixtureId}`, data);

// Friendly Challenges
export const getChallenges = () => API.get('/challenges');
export const getPendingChallengeCount = () => API.get('/challenges/pending-count');
export const getChallengeableTeams = () => API.get('/challenges/teams');
export const sendChallenge = (data) => API.post('/challenges/send', data);
export const acceptChallenge = (id) => API.post(`/challenges/${id}/accept`);
export const declineChallenge = (id) => API.post(`/challenges/${id}/decline`);
export const cancelChallenge = (id) => API.post(`/challenges/${id}/cancel`);
export const simulateChallenge = (id) => API.post(`/challenges/${id}/simulate`);

// Match Result
export const getMatchResult = (fixtureId) => API.get(`/match/result/${fixtureId}`);
export const getCommentary = (fixtureId) => API.get(`/match/commentary/${fixtureId}`);
export const simulateMatch = (fixtureId) => API.post(`/match/simulate/${fixtureId}`);
export const getRivalry = (team1Id, team2Id) =>
  API.get(`/match/rivalry/${team1Id}/${team2Id}`);
export const getFixturePreview = (fixtureId) => API.get(`/match/preview/${fixtureId}`);

// Player
export const getPlayerProfile = (playerId) => API.get(`/player/${playerId}`);

// Stats
export const getTeamStats = (format, matchType) => API.get('/stats', { params: { format, matchType } });

// Academy
export const getAcademyOverview = () => API.get('/academy');
export const upgradeAcademy = () => API.post('/academy/upgrade');
export const downgradeAcademy = () => API.post('/academy/downgrade');
export const pullPlayer = (role) => API.post('/academy/pull', { role });
export const getPullStatus = () => API.get('/academy/pull-status');
export const getPullHistory = () => API.get('/academy/pull-history');
export const assignTraining = (playerId, trainingType) => API.post('/academy/training', { playerId, trainingType });
export const removeTraining = (playerId) => API.delete(`/academy/training/${playerId}`);
export const getTrainingHistory = () => API.get('/academy/training-history');

// Transfer Market
export const listPlayerOnTM = (playerId, startingPrice) => API.post('/transfer/list', { playerId, ...(startingPrice && { startingPrice }) });
export const getActiveListings = () => API.get('/transfer/listings');
export const getMyListings = () => API.get('/transfer/my-listings');
export const placeBid = (listingId, bidAmount) => API.post('/transfer/bid', { listingId, bidAmount });
export const cancelListing = (listingId) => API.post(`/transfer/cancel/${listingId}`);
export const firePlayer = (playerId) => API.post(`/transfer/fire/${playerId}`);
export const retirePlayer = (playerId) => API.post(`/transfer/retire/${playerId}`);
export const getPlayerTransferStatus = (playerId) => API.get(`/transfer/player-status/${playerId}`);
export const getRecentSales = () => API.get('/transfer/recent-sales');

// Finances
export const getFinances = (type) => API.get('/finances', { params: type ? { type } : {} });

// Weather
export const getWeatherForecast = () => API.get('/weather/forecast');

// Activity Feed
export const getRecentActivities = () => API.get('/activity');

// Admin - Simulation Lab
export const createSimSession = (data) => API.post('/admin/sim/sessions', data);
export const listSimSessions = () => API.get('/admin/sim/sessions');
export const getSimSession = (id) => API.get(`/admin/sim/sessions/${id}`);
export const deleteSimSession = (id) => API.delete(`/admin/sim/sessions/${id}`);
