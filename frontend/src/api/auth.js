import API from './axios';

export const signup = (data) => API.post('/auth/signup', data);
export const login = (data) => API.post('/auth/login', data);
export const getMe = () => API.get('/auth/me');
export const setupTeam = (data) => API.post('/team/setup', data);
export const checkCountryAvailability = (country) => API.get(`/team/check-availability?country=${encodeURIComponent(country)}`);

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

// Ground Management
export const getStadiumSeats = () => API.get('/ground/seats');
export const updateStadiumSeats = (data) => API.put('/ground/seats', data);
export const getUpcomingHomeMatches = () => API.get('/ground/matches');
export const updateMatchPitch = (matchId, pitchType) =>
  API.put(`/ground/matches/${matchId}/pitch`, { pitchType });

// Team List
export const getTeamList = () => API.get('/teams');

// Team Profile (public, any team)
export const getTeamProfile = (teamId) => API.get(`/teams/${teamId}`);
export const getTeamSquad = (teamId) => API.get(`/teams/${teamId}/squad`);
export const getTeamMatches = (teamId) => API.get(`/teams/${teamId}/matches`);
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
export const getLeagueDetail = (id) => API.get(`/leagues/${id}`);
export const getLeagueFixtures = (id) => API.get(`/leagues/${id}/fixtures`);
export const getLeaguePlayerStats = (id) => API.get(`/leagues/${id}/stats`);

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

// Lineup
export const getLineupData = (fixtureId) => API.get(`/match/${fixtureId}/lineup`);
export const saveLineup = (fixtureId, data) => API.post(`/match/${fixtureId}/lineup`, data);

// Friendly Challenges
export const getChallenges = () => API.get('/challenges');
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
export const getPullHistory = () => API.get('/academy/pull-history');
export const assignTraining = (playerId, trainingType) => API.post('/academy/training', { playerId, trainingType });
export const removeTraining = (playerId) => API.delete(`/academy/training/${playerId}`);
export const getTrainingHistory = () => API.get('/academy/training-history');

// Weather
export const getWeatherForecast = () => API.get('/weather/forecast');
