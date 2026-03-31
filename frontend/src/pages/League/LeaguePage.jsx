import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getLeagueDetail, getLeagueFixtures, getLeaguePlayerStats } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineGlobeAlt,
  HiOutlineTableCells,
  HiOutlineCalendarDays,
  HiOutlineChartBar,
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
  const [league, setLeague] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('standings');
  const [fixtureData, setFixtureData] = useState(null);
  const [fixturesLoading, setFixturesLoading] = useState(false);
  const [statsData, setStatsData] = useState(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [activeStatTab, setActiveStatTab] = useState('batting');

  useEffect(() => {
    setLoading(true);
    getLeagueDetail(id)
      .then((res) => setLeague(res.data))
      .catch(() => toast.error('Failed to load league'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    if (activeTab === 'fixtures' && !fixtureData) {
      setFixturesLoading(true);
      getLeagueFixtures(id)
        .then((res) => setFixtureData(res.data))
        .catch(() => toast.error('Failed to load fixtures'))
        .finally(() => setFixturesLoading(false));
    }
    if (activeTab === 'stats' && !statsData) {
      setStatsLoading(true);
      getLeaguePlayerStats(id)
        .then((res) => setStatsData(res.data))
        .catch(() => toast.error('Failed to load stats'))
        .finally(() => setStatsLoading(false));
    }
  }, [activeTab, id, fixtureData, statsData]);

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
      {/* Header */}
      <div className="lp-header">
        <HiOutlineGlobeAlt className="lp-header-icon" />
        <div>
          <h1>{league.country} {league.format} {league.leagueId}</h1>
          <p className="lp-subtitle">
            Division {league.division} · League {league.leagueNumber} · Season {league.season} · {league.totalTeams} Teams
            {league.matchStartTimeUtc && <> · Match Time: {league.matchStartTimeUtc} UTC</>}
          </p>
        </div>
      </div>

      {/* Tabs */}
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

      {/* Tab Content */}
      <div className="lp-content">
        {activeTab === 'standings' && (
          <div className="lp-standings">
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
                  <th>NRR</th>
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
                            src={`http://localhost:8080/api/files/${row.teamProfilePicUrl}`}
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
                      {row.nrr >= 0 ? '+' : ''}{row.nrr.toFixed(3)}
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
              <div className="lp-rounds">
                {fixtureData.rounds.map((round) => (
                  <div key={round.round} className="lp-round-block">
                    <div className="lp-round-header">
                      <span className="lp-round-label">Round {round.round}</span>
                      <span className="lp-round-date">
                        {round.matchDate
                          ? new Date(round.matchDate + 'T00:00:00').toLocaleDateString('en-US', {
                              weekday: 'short',
                              month: 'short',
                              day: 'numeric',
                            })
                          : ''}
                        {round.matchStartTimeUtc && (
                          <span className="lp-round-time"> · {round.matchStartTimeUtc} UTC</span>
                        )}
                      </span>
                    </div>
                    <div className="lp-round-matches">
                      {round.matches.map((m) => (
                        <div key={m.id} className="lp-match-card">
                          <div className="lp-match-team lp-match-home">
                            {m.homeTeam.teamProfilePicUrl ? (
                              <img
                                className="lp-match-logo"
                                src={`http://localhost:8080/api/files/${m.homeTeam.teamProfilePicUrl}`}
                                alt={m.homeTeam.teamName}
                              />
                            ) : (
                              <span className="lp-match-initials">
                                {m.homeTeam.teamName?.slice(0, 2).toUpperCase()}
                              </span>
                            )}
                            <span className="lp-match-name">
                              {m.homeTeam.teamName}
                              {m.homeTeam.isBot && <span className="lp-bot-badge">BOT</span>}
                            </span>
                          </div>
                          <span className="lp-match-vs">vs</span>
                          <div className="lp-match-team lp-match-away">
                            <span className="lp-match-name lp-match-name-right">
                              {m.awayTeam.teamName}
                              {m.awayTeam.isBot && <span className="lp-bot-badge">BOT</span>}
                            </span>
                            {m.awayTeam.teamProfilePicUrl ? (
                              <img
                                className="lp-match-logo"
                                src={`http://localhost:8080/api/files/${m.awayTeam.teamProfilePicUrl}`}
                                alt={m.awayTeam.teamName}
                              />
                            ) : (
                              <span className="lp-match-initials">
                                {m.awayTeam.teamName?.slice(0, 2).toUpperCase()}
                              </span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
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
                <p>No stats available yet — matches haven't been played</p>
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

                {/* Batting Stats */}
                {activeStatTab === 'batting' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player">Player</th>
                          <th className="lp-st-team">Team</th>
                          <th>Bat</th>
                          <th>M</th>
                          <th>Inn</th>
                          <th>NO</th>
                          <th>Runs</th>
                          <th>BF</th>
                          <th>HS</th>
                          <th>SR</th>
                          <th>Avg</th>
                          <th>100s</th>
                          <th>50s</th>
                          <th>4s</th>
                          <th>6s</th>
                          <th>0s</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statsData.batting.map((b, i) => (
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

                {/* Bowling Stats */}
                {activeStatTab === 'bowling' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player">Player</th>
                          <th className="lp-st-team">Team</th>
                          <th>Type</th>
                          <th>M</th>
                          <th>Inn</th>
                          <th>Balls</th>
                          <th>Mdns</th>
                          <th>Runs</th>
                          <th className="lp-st-highlight-head">Wkts</th>
                          <th>BB</th>
                          <th>Avg</th>
                          <th>SR</th>
                          <th>Econ</th>
                          <th>3WI</th>
                          <th>5WI</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statsData.bowling.map((b, i) => (
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

                {/* Fielding Stats */}
                {activeStatTab === 'fielding' && (
                  <div className="lp-stat-table-wrap">
                    <table className="lp-stat-table">
                      <thead>
                        <tr>
                          <th className="lp-st-pos">#</th>
                          <th className="lp-st-player">Player</th>
                          <th className="lp-st-team">Team</th>
                          <th>M</th>
                          <th>Catches</th>
                          <th>Stumpings</th>
                          <th>Runouts</th>
                          <th className="lp-st-highlight-head">Total</th>
                        </tr>
                      </thead>
                      <tbody>
                        {statsData.fielding.map((f, i) => (
                          <tr key={f.playerId}>
                            <td className="lp-st-pos">{i + 1}</td>
                            <td className="lp-st-player">{f.playerName}</td>
                            <td className="lp-st-team">{f.teamName}</td>
                            <td>{f.matches}</td>
                            <td>{f.catches}</td>
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
