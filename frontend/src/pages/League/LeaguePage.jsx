import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getLeagueDetail, getLeagueFixtures } from '../../api/auth';
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

export default function LeaguePage() {
  const { id } = useParams();
  const [league, setLeague] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('standings');
  const [fixtureData, setFixtureData] = useState(null);
  const [fixturesLoading, setFixturesLoading] = useState(false);

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
  }, [activeTab, id, fixtureData]);

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
          <div className="lp-coming-soon">
            <HiOutlineChartBar className="lp-coming-icon" />
            <p>Stats coming soon</p>
          </div>
        )}
      </div>
    </div>
  );
}
