import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { getLeagueDetail, getLeagueFixtures, getLeaguePlayerStats, getAvailableLeagues, refreshLeagueStandings } from '../../api/auth';
import { COUNTRIES } from '../../constants/countries';
import toast from 'react-hot-toast';
import {
  HiOutlineGlobeAlt,
  HiOutlineTableCells,
  HiOutlineCalendarDays,
  HiOutlineChartBar,
  HiOutlineArrowPath,
} from 'react-icons/hi2';
import './LeaguePage.css';

const TABS = [
  { key: 'standings', label: 'Points Table', icon: HiOutlineTableCells },
  { key: 'fixtures', label: 'Fixtures', icon: HiOutlineCalendarDays },
  { key: 'stats', label: 'Stats', icon: HiOutlineChartBar },
];

const STAT_TABS = [
  { key: 'batting', label: 'Batting' },
  { key: 'bowling', label: 'Bowling' },
  { key: 'fielding', label: 'Fielding' },
];

export default function LeaguePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [league, setLeague] = useState(null);
  const [season, setSeason] = useState(() => {
    const raw = searchParams.get('season');
    return raw ? Number(raw) : null;
  });
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState(() => sessionStorage.getItem('league_tab') || 'standings');
  const [fixtureData, setFixtureData] = useState(null);
  const [fixturesLoading, setFixturesLoading] = useState(false);
  const [statsData, setStatsData] = useState(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [activeStatTab, setActiveStatTab] = useState(() => sessionStorage.getItem('league_stat_tab') || 'batting');

  // League Filter States
  const [selectedCountry, setSelectedCountry] = useState(null);
  const [selectedFormat, setSelectedFormat] = useState(null);
  const [selectedDivision, setSelectedDivision] = useState(null);
  const [availableLeaguesData, setAvailableLeaguesData] = useState(null);
  const [filterLoading, setFilterLoading] = useState(false);

  // Sorting States
  const [sortField, setSortField] = useState(null);
  const [sortOrder, setSortOrder] = useState('desc');

  // Refresh Standings State
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    setLoading(true);
    getLeagueDetail(id, season)
      .then((res) => {
        setLeague(res.data);
        if (season == null && res.data?.season != null) {
          setSeason(res.data.season);
        }
        // Initialize filter selections from current league
        if (res.data?.country && !selectedCountry) {
          setSelectedCountry(res.data.country);
          setSelectedFormat(res.data.format);
          setSelectedDivision(res.data.division);
        }
      })
      .catch(() => toast.error('Failed to load league'))
      .finally(() => setLoading(false));
  }, [id, season]);

  // Load available leagues for the selected country and format
  const loadAvailableLeagues = useCallback(async (country, format) => {
    if (!country || !format) return;
    setFilterLoading(true);
    try {
      const res = await getAvailableLeagues(country, format);
      setAvailableLeaguesData(res.data);
    } catch (err) {
      console.error('Failed to load available leagues:', err);
      toast.error('Failed to load available leagues');
    } finally {
      setFilterLoading(false);
    }
  }, []);

  // Load available leagues when country or format changes
  useEffect(() => {
    if (selectedCountry && selectedFormat) {
      loadAvailableLeagues(selectedCountry, selectedFormat);
    }
  }, [selectedCountry, selectedFormat, loadAvailableLeagues]);

  useEffect(() => {
    sessionStorage.setItem('league_tab', activeTab);
  }, [activeTab]);

  // Navigate to a different league based on filter selection
  const handleNavigateToLeague = useCallback((leagueId) => {
    navigate(`/league/${leagueId}`);
  }, [navigate]);

  // Handle standings refresh
  const handleRefreshStandings = useCallback(async () => {
    setRefreshing(true);
    try {
      const fixRes = await refreshLeagueStandings(id, season);
      const fixed = fixRes.data?.fixedMatches ?? 0;

      // Reload full league detail so standings re-render from corrected DB data
      const detailRes = await getLeagueDetail(id, season);
      setLeague(detailRes.data);

      if (fixed > 0) {
        toast.success(`Sync complete — ${fixed} match result(s) corrected`);
      } else {
        toast.success(fixRes.data?.message ?? 'Standings are up to date');
      }
    } catch (error) {
      console.error('Failed to sync standings:', error);
      toast.error('Failed to sync standings');
    } finally {
      setRefreshing(false);
    }
  }, [id, season]);

  // Handle column sorting
  const handleSort = useCallback((field) => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('desc');
    }
  }, [sortField, sortOrder]);

  // Sort data based on current sort field and order
  const sortData = useCallback((data, field, order) => {
    if (!field || !data) return data;
    
    const sorted = [...data].sort((a, b) => {
      let aVal = a[field];
      let bVal = b[field];

      // Handle string comparisons
      if (typeof aVal === 'string' && typeof bVal === 'string') {
        return order === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }

      // Handle numeric comparisons
      aVal = Number(aVal) || 0;
      bVal = Number(bVal) || 0;
      return order === 'asc' ? aVal - bVal : bVal - aVal;
    });

    return sorted;
  }, []);

  // Get sorted stats data
  const getSortedStatsData = useCallback(() => {
    if (!statsData) return statsData;
    
    let data = statsData;
    if (activeStatTab === 'batting' && sortField) {
      return {
        ...data,
        batting: sortData(data.batting, sortField, sortOrder),
      };
    } else if (activeStatTab === 'bowling' && sortField) {
      return {
        ...data,
        bowling: sortData(data.bowling, sortField, sortOrder),
      };
    } else if (activeStatTab === 'fielding' && sortField) {
      return {
        ...data,
        fielding: sortData(data.fielding, sortField, sortOrder),
      };
    }
    return data;
  }, [statsData, activeStatTab, sortField, sortOrder, sortData]);

  useEffect(() => {
    sessionStorage.setItem('league_stat_tab', activeStatTab);
  }, [activeStatTab]);

  useEffect(() => {
    setFixtureData(null);
    setStatsData(null);
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (season) next.set('season', String(season));
      else next.delete('season');
      return next;
    });
  }, [season, setSearchParams]);

  useEffect(() => {
    if (activeTab === 'fixtures' && !fixtureData) {
      setFixturesLoading(true);
      getLeagueFixtures(id, season)
        .then((res) => setFixtureData(res.data))
        .catch(() => toast.error('Failed to load fixtures'))
        .finally(() => setFixturesLoading(false));
    }
    if (activeTab === 'stats' && !statsData) {
      setStatsLoading(true);
      getLeaguePlayerStats(id, season)
        .then((res) => setStatsData(res.data))
        .catch(() => toast.error('Failed to load stats'))
        .finally(() => setStatsLoading(false));
    }
  }, [activeTab, id, fixtureData, statsData, season]);

  if (loading) {
    return (
      <div className="lp-page">
        <div className="lp-loading">Loading league...</div>
      </div>
    );
  }

  if (!league) {
    return (
      <div className="lp-page">
        <div className="lp-loading">League not found.</div>
      </div>
    );
  }

  return (
    <div className="lp-page">
      <div className="lp-header">
        <HiOutlineGlobeAlt className="lp-header-icon" />
        <div>
          <h1>{league.country} {league.format} {league.leagueId}</h1>
          <p className="lp-subtitle">
            Division {league.division} · League {league.leagueNumber} · Season {league.season} · {league.totalTeams} Teams
            {league.matchStartTimeUtc && <> · Match Time: {league.matchStartTimeUtc} UTC</>}
          </p>
          {league.availableSeasons?.length > 1 && (
            <div style={{ marginTop: 12 }}>
              <select value={season ?? league.season} onChange={(e) => setSeason(Number(e.target.value))}>
                {league.availableSeasons.map((s) => (
                  <option key={s} value={s}>Season {s}</option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* ── League Filters ── */}
      <div className="lp-filters-container">
        <div className="lp-filters">
          {/* Country Filter */}
          <div className="lp-filter-group">
            <label className="lp-filter-label">Country:</label>
            <select
              className="lp-filter-select"
              value={selectedCountry || ''}
              onChange={(e) => setSelectedCountry(e.target.value)}
            >
              <option value="">Select country</option>
              {COUNTRIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>

          {/* Format Filter */}
          {selectedCountry && (
            <div className="lp-filter-group">
              <span className="lp-filter-label">Format:</span>
              <div className="lp-format-buttons">
                {['T20', 'ODI', 'FC'].map((fmt) => (
                  <button
                    key={fmt}
                    className={`lp-format-btn ${selectedFormat === fmt ? 'active' : ''}`}
                    onClick={() => setSelectedFormat(fmt)}
                  >
                    {fmt}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Division Filter */}
          {selectedCountry && selectedFormat && availableLeaguesData?.divisions && (
            <div className="lp-filter-group">
              <label className="lp-filter-label">Division:</label>
              <select
                className="lp-filter-select"
                value={selectedDivision || ''}
                onChange={(e) => {
                  const div = parseInt(e.target.value);
                  setSelectedDivision(div);
                }}
              >
                <option value="">Select division</option>
                {Object.keys(availableLeaguesData.divisions)
                  .sort((a, b) => parseInt(a) - parseInt(b))
                  .map((div) => (
                    <option key={div} value={div}>
                      Division {div}
                    </option>
                  ))}
              </select>
            </div>
          )}

          {/* League Selection */}
          {selectedCountry && selectedFormat && selectedDivision && availableLeaguesData?.divisions[selectedDivision]?.length > 0 && (
            <div className="lp-filter-group">
              <label className="lp-filter-label">League:</label>
              <select
                className="lp-filter-select"
                value={id || ''}
                onChange={(e) => handleNavigateToLeague(e.target.value)}
              >
                <option value="">Select league</option>
                {availableLeaguesData.divisions[selectedDivision].map((league) => (
                  <option key={league.id} value={league.id}>
                    {league.format} League {league.leagueNumber}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      <div className="lp-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`lp-tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            <tab.icon className="lp-tab-icon" />
            {tab.label}
          </button>
        ))}
      </div>

      <div className="lp-content">
        {activeTab === 'standings' && (
          <div className="lp-standings">
            <div className="lp-standings-header">
              <button
                className="lp-refresh-btn"
                onClick={handleRefreshStandings}
                disabled={refreshing}
                title="Refresh standings from latest match results"
              >
                <HiOutlineArrowPath className={`lp-refresh-icon ${refreshing ? 'spinning' : ''}`} />
                {refreshing ? 'Refreshing...' : 'Refresh'}
              </button>
            </div>
            <table className="lp-table">
              <thead>
                <tr>
                  <th className="lp-th-pos">#</th>
                  <th className="lp-th-team">Team</th>
                  <th>P</th>
                  <th>W</th>
                  <th>L</th>
                  <th>T</th>
                  <th>Pts</th>
                  <th>{league.format === 'FC' ? 'Quo' : 'NRR'}</th>
                </tr>
              </thead>
              <tbody>
                {league.standings.map((row) => (
                  <tr key={row.teamId}>
                    <td className="lp-td-pos">{row.position}</td>
                    <td className="lp-td-team">
                      <div className="lp-team-cell">
                        {row.teamProfilePicUrl ? (
                          <img
                            className="lp-team-logo"
                            src={`/api/files/${row.teamProfilePicUrl}`}
                            alt={row.teamName}
                          />
                        ) : (
                          <span className="lp-team-initials">
                            {row.teamName?.slice(0, 2).toUpperCase()}
                          </span>
                        )}
                        <span className="lp-team-name">{row.teamName}</span>
                        {row.isBot && <span className="lp-bot-badge">BOT</span>}
                      </div>
                    </td>
                    <td>{row.played}</td>
                    <td>{row.won}</td>
                    <td>{row.lost}</td>
                    <td>{row.tied}</td>
                    <td className="lp-td-pts">{row.points}</td>
                    <td className="lp-td-nrr">
                      {league.format === 'FC'
                        ? row.nrr.toFixed(3)
                        : `${row.nrr >= 0 ? '+' : ''}${row.nrr.toFixed(3)}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {activeTab === 'fixtures' && (
          <div className="lp-fixtures">
            {fixturesLoading ? (
              <div className="lp-loading">Loading fixtures...</div>
            ) : !fixtureData || fixtureData.rounds.length === 0 ? (
              <div className="lp-coming-soon">
                <HiOutlineCalendarDays className="lp-coming-icon" />
                <p>No fixtures generated yet</p>
              </div>
            ) : (
              <div className="lp-fixture-table">
                {/* Table header */}
                <div className="lp-ft-head">
                  <span className="lp-ft-col-fixture">Fixture</span>
                  <span className="lp-ft-col-result">Result / Status</span>
                </div>

                {fixtureData.rounds.map((round) => (
                  <div key={round.round}>
                    {/* Round divider */}
                    <div className="lp-ft-round-divider">
                      <span className="lp-ft-round-label">Round {round.round}</span>
                      <span className="lp-ft-round-meta">
                        {round.matchDate
                          ? new Date(round.matchDate + 'T00:00:00Z').toLocaleDateString('en-US', {
                              weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC',
                            })
                          : ''}
                        {round.matchStartTimeUtc && (
                          <span className="lp-ft-round-time"> · {round.matchStartTimeUtc} UTC</span>
                        )}
                      </span>
                    </div>

                    {/* Match rows */}
                    {round.matches.map((m) => (
                      <div
                        key={m.id}
                        className="lp-ft-row"
                        onClick={() => {
                          if (m.status === 'FC_DAY1_COMPLETE') navigate(`/match/${m.id}/fc-strategy`);
                          else if (m.status === 'COMPLETED' || m.status === 'IN_PROGRESS') navigate(`/match/${m.id}?tab=scorecard`);
                          else navigate(`/match/${m.id}/preview`);
                        }}
                      >
                        {/* Fixture column */}
                        <div className="lp-ft-col-fixture">
                          {/* Home */}
                          <div className="lp-ft-team">
                            {m.homeTeam.teamProfilePicUrl ? (
                              <img className="lp-ft-logo" src={`/api/files/${m.homeTeam.teamProfilePicUrl}`} alt={m.homeTeam.teamName} />
                            ) : (
                              <span className="lp-ft-initials">{m.homeTeam.teamName?.slice(0, 2).toUpperCase()}</span>
                            )}
                            <span className="lp-ft-name">{m.homeTeam.teamName}</span>
                            {m.homeTeam.isBot && <span className="lp-ft-bot">BOT</span>}
                          </div>
                          <span className="lp-ft-vs">vs</span>
                          {/* Away */}
                          <div className="lp-ft-team">
                            {m.awayTeam.teamProfilePicUrl ? (
                              <img className="lp-ft-logo" src={`/api/files/${m.awayTeam.teamProfilePicUrl}`} alt={m.awayTeam.teamName} />
                            ) : (
                              <span className="lp-ft-initials">{m.awayTeam.teamName?.slice(0, 2).toUpperCase()}</span>
                            )}
                            <span className="lp-ft-name">{m.awayTeam.teamName}</span>
                            {m.awayTeam.isBot && <span className="lp-ft-bot">BOT</span>}
                          </div>
                        </div>

                        {/* Result / Status column */}
                        <div className="lp-ft-col-result">
                          {m.status === 'COMPLETED' && (
                            <span className="lp-ft-result">{m.resultSummary || 'Completed'}</span>
                          )}
                          {m.status === 'IN_PROGRESS' && (
                            <span className="lp-ft-pill lp-ft-pill-live">● LIVE</span>
                          )}
                          {m.status === 'FC_DAY1_COMPLETE' && (
                            <span className="lp-ft-pill lp-ft-pill-live">Day 1 Done</span>
                          )}
                          {m.status === 'SCHEDULED' && (
                            <span className="lp-ft-pill lp-ft-pill-scheduled">Scheduled</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'stats' && (
          <div className="lp-stats">
            {statsLoading ? (
              <div className="lp-loading">Loading stats...</div>
            ) : !statsData || (statsData.batting.length === 0 && statsData.bowling.length === 0 && statsData.fielding.length === 0) ? (
              <div className="lp-coming-soon">
                <HiOutlineChartBar className="lp-coming-icon" />
                <p>No stats available yet - matches haven&apos;t been played</p>
              </div>
            ) : (
              <>
                <div className="lp-stat-tabs">
                  {STAT_TABS.map((st) => (
                    <button
                      key={st.key}
                      className={`lp-stat-tab ${activeStatTab === st.key ? 'active' : ''}`}
                      onClick={() => setActiveStatTab(st.key)}
                    >
                      {st.label}
                    </button>
                  ))}
                </div>

                {activeStatTab === 'batting' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player lp-sortable" onClick={() => handleSort('playerName')}>
                            Player {sortField === 'playerName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-st-team lp-sortable" onClick={() => handleSort('teamName')}>
                            Team {sortField === 'teamName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('batHand')}>
                            Bat {sortField === 'batHand' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('matches')}>
                            M {sortField === 'matches' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('innings')}>
                            Inn {sortField === 'innings' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('notOuts')}>
                            NO {sortField === 'notOuts' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('runs')}>
                            Runs {sortField === 'runs' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('balls')}>
                            BF {sortField === 'balls' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('highScore')}>
                            HS {sortField === 'highScore' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('strikeRate')}>
                            SR {sortField === 'strikeRate' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('average')}>
                            Avg {sortField === 'average' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('hundreds')}>
                            100s {sortField === 'hundreds' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('fifties')}>
                            50s {sortField === 'fifties' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('fours')}>
                            4s {sortField === 'fours' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('sixes')}>
                            6s {sortField === 'sixes' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('ducks')}>
                            0s {sortField === 'ducks' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {getSortedStatsData().batting.map((b, i) => (
                          <tr key={b.playerId}>
                            <td className="lp-st-pos">{i + 1}</td>
                            <td className="lp-st-player">{b.playerName}</td>
                            <td className="lp-st-team">{b.teamName}</td>
                            <td>{b.batHand}</td>
                            <td>{b.matches}</td>
                            <td>{b.innings}</td>
                            <td>{b.notOuts}</td>
                            <td className="lp-st-highlight">{b.runs}</td>
                            <td>{b.balls}</td>
                            <td>{b.highScore}</td>
                            <td>{b.strikeRate}</td>
                            <td>{b.average}</td>
                            <td>{b.hundreds}</td>
                            <td>{b.fifties}</td>
                            <td>{b.fours}</td>
                            <td>{b.sixes}</td>
                            <td>{b.ducks}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {activeStatTab === 'bowling' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player lp-sortable" onClick={() => handleSort('playerName')}>
                            Player {sortField === 'playerName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-st-team lp-sortable" onClick={() => handleSort('teamName')}>
                            Team {sortField === 'teamName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('bowlType')}>
                            Type {sortField === 'bowlType' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('matches')}>
                            M {sortField === 'matches' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('innings')}>
                            Inn {sortField === 'innings' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('balls')}>
                            Balls {sortField === 'balls' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('maidens')}>
                            Mdns {sortField === 'maidens' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('runs')}>
                            Runs {sortField === 'runs' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('wickets')}>
                            Wkts {sortField === 'wickets' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('bestBowling')}>
                            BB {sortField === 'bestBowling' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('average')}>
                            Avg {sortField === 'average' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('strikeRate')}>
                            SR {sortField === 'strikeRate' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('economy')}>
                            Econ {sortField === 'economy' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('threeWI')}>
                            3WI {sortField === 'threeWI' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('fiveWI')}>
                            5WI {sortField === 'fiveWI' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {getSortedStatsData().bowling.map((b, i) => (
                          <tr key={b.playerId}>
                            <td className="lp-st-pos">{i + 1}</td>
                            <td className="lp-st-player">{b.playerName}</td>
                            <td className="lp-st-team">{b.teamName}</td>
                            <td>{b.bowlType}</td>
                            <td>{b.matches}</td>
                            <td>{b.innings}</td>
                            <td>{b.balls}</td>
                            <td>{b.maidens}</td>
                            <td>{b.runs}</td>
                            <td className="lp-st-highlight">{b.wickets}</td>
                            <td>{b.bestBowling}</td>
                            <td>{b.average}</td>
                            <td>{b.strikeRate}</td>
                            <td>{b.economy}</td>
                            <td>{b.threeWI}</td>
                            <td>{b.fiveWI}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {activeStatTab === 'fielding' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player lp-sortable" onClick={() => handleSort('playerName')}>
                            Player {sortField === 'playerName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-st-team lp-sortable" onClick={() => handleSort('teamName')}>
                            Team {sortField === 'teamName' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('matches')}>
                            M {sortField === 'matches' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('fielderCatches')}>
                            Catches {sortField === 'fielderCatches' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('keeperCatches')}>
                            Keeper Ct {sortField === 'keeperCatches' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('stumpings')}>
                            Stumpings {sortField === 'stumpings' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('runouts')}>
                            Runouts {sortField === 'runouts' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                          <th className="lp-sortable" onClick={() => handleSort('total')}>
                            Total {sortField === 'total' && <span className="lp-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {getSortedStatsData().fielding.map((f, i) => (
                          <tr key={f.playerId}>
                            <td className="lp-st-pos">{i + 1}</td>
                            <td className="lp-st-player">{f.playerName}</td>
                            <td className="lp-st-team">{f.teamName}</td>
                            <td>{f.matches}</td>
                            <td>{f.fielderCatches}</td>
                            <td>{f.keeperCatches}</td>
                            <td>{f.stumpings}</td>
                            <td>{f.runouts}</td>
                            <td className="lp-st-highlight">{f.total}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

