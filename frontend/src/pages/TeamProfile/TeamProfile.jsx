import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  getTeamProfile,
  getTeamSquad,
  getTeamMatches,
  getTeamLeagues,
  getTeamGround,
  getCurrentSeason,
} from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineCalendarDays,
  HiOutlineUserGroup,
  HiOutlineGlobeAlt,
  HiOutlineBuildingOffice2,
  HiOutlineFunnel,
} from 'react-icons/hi2';
import './TeamProfile.css';

const TABS = [
  { key: 'matches', label: 'Matches', icon: HiOutlineCalendarDays },
  { key: 'squad', label: 'Squad', icon: HiOutlineUserGroup },
  { key: 'leagues', label: 'Leagues', icon: HiOutlineGlobeAlt },
  { key: 'ground', label: 'Ground', icon: HiOutlineBuildingOffice2 },
];

const FORMAT_COLORS = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#34d399' };
const ROLE_ORDER = { BATSMAN: 0, KEEPER: 1, ALL_ROUNDER: 2, BOWLER: 3 };
const ROLE_LABEL = { BATSMAN: 'Batsman', KEEPER: 'Keeper', ALL_ROUNDER: 'All-Rounder', BOWLER: 'Bowler' };

const ratingColor = (r) => {
  if (r >= 1400) return '#22d3ee';
  if (r >= 1200) return '#a3e635';
  if (r >= 1000) return '#fbbf24';
  if (r >= 800) return '#f97316';
  return '#ef4444';
};

export default function TeamProfile() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const [team, setTeam] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('matches');

  const [matches, setMatches] = useState(null);
  const [matchesLoading, setMatchesLoading] = useState(false);
  const [matchFilterFormat, setMatchFilterFormat] = useState('');
  const [matchViewTab, setMatchViewTab] = useState('upcoming');
  const [matchSeason, setMatchSeason] = useState(null);
  const [maxSeason, setMaxSeason] = useState(1);
  const [squad, setSquad] = useState(null);
  const [squadLoading, setSquadLoading] = useState(false);
  const [leagues, setLeagues] = useState(null);
  const [leaguesLoading, setLeaguesLoading] = useState(false);
  const [ground, setGround] = useState(null);
  const [groundLoading, setGroundLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getTeamProfile(teamId)
      .then((res) => setTeam(res.data))
      .catch(() => toast.error('Failed to load team'))
      .finally(() => setLoading(false));
  }, [teamId]);

  useEffect(() => {
    getCurrentSeason()
      .then((res) => {
        const s = res.data.season;
        setMaxSeason(s);
        setMatchSeason(s);
      })
      .catch(() => { setMaxSeason(1); setMatchSeason(1); });
  }, []);

  useEffect(() => {
    if (activeTab === 'matches' && matchSeason != null) {
      setMatchesLoading(true);
      setMatches(null);
      getTeamMatches(teamId, matchSeason)
        .then((res) => setMatches(res.data))
        .catch(() => toast.error('Failed to load matches'))
        .finally(() => setMatchesLoading(false));
    }
    if (activeTab === 'squad' && !squad) {
      setSquadLoading(true);
      getTeamSquad(teamId)
        .then((res) => setSquad(res.data))
        .catch(() => toast.error('Failed to load squad'))
        .finally(() => setSquadLoading(false));
    }
    if (activeTab === 'leagues' && !leagues) {
      setLeaguesLoading(true);
      getTeamLeagues(teamId)
        .then((res) => setLeagues(res.data))
        .catch(() => toast.error('Failed to load leagues'))
        .finally(() => setLeaguesLoading(false));
    }
    if (activeTab === 'ground' && !ground) {
      setGroundLoading(true);
      getTeamGround(teamId)
        .then((res) => setGround(res.data))
        .catch(() => toast.error('Failed to load ground info'))
        .finally(() => setGroundLoading(false));
    }
  }, [activeTab, teamId, matches, squad, leagues, ground]);

  if (loading) {
    return (
      <div className="tp-page">
        <div className="tp-loading">Loading team...</div>
      </div>
    );
  }

  if (!team) {
    return (
      <div className="tp-page">
        <div className="tp-loading">Team not found.</div>
      </div>
    );
  }

  return (
    <div className="tp-page">
      {/* Header */}
      <div className="tp-header">
        <div className="tp-header-left">
          {team.teamProfilePicUrl ? (
            <img
              className="tp-header-logo"
              src={`/api/files/${team.teamProfilePicUrl}`}
              alt={team.teamName}
            />
          ) : (
            <span className="tp-header-initials">
              {team.teamName?.slice(0, 2).toUpperCase()}
            </span>
          )}
          <div>
            <h1>{team.teamName}</h1>
            <p className="tp-subtitle">
              {team.country}
              {team.managerName && <> · Manager: {team.managerName}</>}
              {team.isBot && <span className="tp-bot-badge">BOT</span>}
            </p>
          </div>
        </div>
        <div className="tp-header-ratings">
          <div className="tp-rating-item">
            <span className="tp-rating-label">ODI</span>
            <span className="tp-rating-value" style={{ color: ratingColor(team.odiRating) }}>{team.odiRating}</span>
          </div>
          <div className="tp-rating-item">
            <span className="tp-rating-label">T20</span>
            <span className="tp-rating-value" style={{ color: ratingColor(team.t20Rating) }}>{team.t20Rating}</span>
          </div>
          <div className="tp-rating-item">
            <span className="tp-rating-label">FC</span>
            <span className="tp-rating-value" style={{ color: ratingColor(team.fcRating) }}>{team.fcRating}</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="tp-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`tp-tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            <tab.icon className="tp-tab-icon" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <div className="tp-content">

        {/* ── Matches Tab ── */}
        {activeTab === 'matches' && (
          <div className="tp-matches">
            {matchesLoading ? (
              <div className="tp-loading">Loading matches...</div>
            ) : (
              <>
                {/* Filter bar */}
                <div className="tp-match-filters">
                  <div className="tp-match-filter-group">
                    <HiOutlineFunnel className="tp-mf-icon" />
                    <div className="tp-mf-item">
                      <label>Season</label>
                      <select value={matchSeason ?? ''} onChange={(e) => setMatchSeason(Number(e.target.value))}>
                        {Array.from({ length: maxSeason }, (_, i) => i + 1).map((s) => (
                          <option key={s} value={s}>Season {s}</option>
                        ))}
                      </select>
                    </div>
                    <div className="tp-mf-item">
                      <label>Format</label>
                      <select value={matchFilterFormat} onChange={(e) => setMatchFilterFormat(e.target.value)}>
                        <option value="">All Formats</option>
                        <option value="T20">T20</option>
                        <option value="ODI">One Day</option>
                        <option value="FC">First Class</option>
                      </select>
                    </div>
                  </div>
                  <div className="tp-match-tabs">
                    {(() => {
                      const today = new Date().toISOString().split('T')[0];
                      const filtered = (matches || []).filter(m => !matchFilterFormat || m.format === matchFilterFormat);
                      const upcoming = filtered.filter(m => m.status === 'IN_PROGRESS' || m.status === 'FC_DAY1_COMPLETE' || m.status === 'LIVE' || (m.matchDate >= today && m.status === 'SCHEDULED'));
                      const past = filtered.filter(m => m.status === 'COMPLETED');
                      return (
                        <>
                          <button className={`tp-mtab ${matchViewTab === 'upcoming' ? 'active' : ''}`} onClick={() => setMatchViewTab('upcoming')}>
                            <HiOutlineCalendarDays /> Upcoming ({upcoming.length})
                          </button>
                          <button className={`tp-mtab ${matchViewTab === 'past' ? 'active' : ''}`} onClick={() => setMatchViewTab('past')}>
                            <HiOutlineTrophy /> Past ({past.length})
                          </button>
                        </>
                      );
                    })()}
                  </div>
                </div>

                {/* Table */}
                {(() => {
                  const today = new Date().toISOString().split('T')[0];
                  const filtered = (matches || []).filter(m => !matchFilterFormat || m.format === matchFilterFormat);
                  const upcoming = filtered.filter(m => m.status === 'IN_PROGRESS' || m.status === 'FC_DAY1_COMPLETE' || m.status === 'LIVE' || (m.matchDate >= today && m.status === 'SCHEDULED'));
                  const past = filtered.filter(m => m.status === 'COMPLETED').slice().reverse();
                  const displayed = matchViewTab === 'upcoming' ? upcoming : past;

                  const formatDate = (dateStr) => {
                    const d = new Date(dateStr + 'T00:00:00');
                    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
                  };

                  if (displayed.length === 0) {
                    return (
                      <div className="tp-empty">
                        <HiOutlineCalendarDays className="tp-empty-icon" />
                        <p>{matchViewTab === 'upcoming' ? 'No upcoming matches.' : 'No past matches.'}</p>
                      </div>
                    );
                  }

                  return (
                    <div className="tp-match-list">
                      <div className="tp-match-head">
                        <span className="tp-mc-date">Date</span>
                        <span className="tp-mc-format">Format</span>
                        <span className="tp-mc-fixture">Fixture</span>
                        <span className="tp-mc-result">{matchViewTab === 'upcoming' ? 'Status' : 'Result'}</span>
                      </div>
                      {displayed.map((m) => {
                        const fmtColor = FORMAT_COLORS[m.format] || '#94a3b8';
                        return (
                          <div
                            key={m.id}
                            className="tp-match-row tp-match-clickable"
                            onClick={() => {
                              if (m.status === 'COMPLETED') navigate(`/match/${m.id}/scorecard`);
                              else if (m.status === 'IN_PROGRESS' || m.status === 'LIVE') navigate(`/match/${m.id}/live`);
                              else if (m.status === 'FC_DAY1_COMPLETE') navigate(`/match/${m.id}/fc-strategy`);
                              else navigate(`/match/${m.id}/preview`);
                            }}
                          >
                            {/* Date */}
                            <div className="tp-mc-date">
                              <span className="tp-mrow-date">{formatDate(m.matchDate)}</span>
                              <span className="tp-mrow-sub">
                                {m.matchType === 'FRIENDLY' ? 'Friendly' : `R${m.round} · Div ${m.leagueLabel}`}
                              </span>
                              {m.matchStartTimeUtc && <span className="tp-mrow-time">{m.matchStartTimeUtc} UTC</span>}
                            </div>

                            {/* Format */}
                            <div className="tp-mc-format">
                              <span
                                className="tp-mrow-fmt"
                                style={{ background: fmtColor + '18', color: fmtColor, borderColor: fmtColor + '40' }}
                              >
                                {m.matchType === 'FRIENDLY' ? m.format + ' F' : m.format}
                              </span>
                            </div>

                            {/* Fixture */}
                            <div className="tp-mc-fixture">
                              <div className="tp-mrow-fixture">
                                <div className="tp-mrow-team">
                                  {m.homeTeamPicUrl
                                    ? <img className="tp-mrow-logo" src={`/api/files/${m.homeTeamPicUrl}`} alt="" />
                                    : <span className="tp-mrow-initials">{m.homeTeamName?.slice(0, 2).toUpperCase()}</span>}
                                  <span className={`tp-mrow-name ${String(m.homeTeamId) === teamId ? 'tp-mrow-mine' : ''}`}>{m.homeTeamName}</span>
                                </div>
                                <span className="tp-mrow-vs">vs</span>
                                <div className="tp-mrow-team">
                                  {m.awayTeamPicUrl
                                    ? <img className="tp-mrow-logo" src={`/api/files/${m.awayTeamPicUrl}`} alt="" />
                                    : <span className="tp-mrow-initials">{m.awayTeamName?.slice(0, 2).toUpperCase()}</span>}
                                  <span className={`tp-mrow-name ${String(m.awayTeamId) === teamId ? 'tp-mrow-mine' : ''}`}>{m.awayTeamName}</span>
                                </div>
                              </div>
                            </div>

                            {/* Result / Status */}
                            <div className="tp-mc-result">
                              {m.status === 'COMPLETED' && (
                                <span className={`tp-mrow-result${
                                  m.resultSummary?.startsWith('Won') ? ' tp-res-won'
                                  : m.resultSummary?.startsWith('Lost') ? ' tp-res-lost'
                                  : ' tp-res-draw'
                                }`}>
                                  {m.resultSummary || 'Completed'}
                                </span>
                              )}
                              {(m.status === 'IN_PROGRESS' || m.status === 'LIVE') && (
                                <span className="tp-res-live">● Live</span>
                              )}
                              {m.status === 'FC_DAY1_COMPLETE' && (
                                <span className="tp-res-day2">Day 2</span>
                              )}
                              {m.status === 'SCHEDULED' && (
                                <span className="tp-res-sched">Scheduled</span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  );
                })()}
              </>
            )}
          </div>
        )}

        {/* ── Squad Tab ── */}
        {activeTab === 'squad' && (
          <div className="tp-squad">
            {squadLoading ? (
              <div className="tp-loading">Loading squad...</div>
            ) : !squad || squad.players.length === 0 ? (
              <div className="tp-empty">
                <HiOutlineUserGroup className="tp-empty-icon" />
                <p>No players found</p>
              </div>
            ) : (
              <div className="tp-squad-table-wrap">
                <table className="tp-squad-table">
                  <thead>
                    <tr>
                      <th className="tp-sq-pos">#</th>
                      <th className="tp-sq-name">Player</th>
                      <th>Role</th>
                      <th>Age</th>
                      <th>Bat</th>
                      <th>Bowl</th>
                      <th>Fld</th>
                      <th>Rating</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...squad.players]
                      .sort((a, b) => (ROLE_ORDER[a.role] ?? 9) - (ROLE_ORDER[b.role] ?? 9))
                      .map((p, i) => (
                        <tr
                          key={p.id}
                          className="tp-sq-row"
                          onClick={() => navigate(`/player/${p.id}`)}
                        >
                          <td className="tp-sq-pos">{i + 1}</td>
                          <td className="tp-sq-name">{p.firstName} {p.lastName}</td>
                          <td>
                            <span className="tp-sq-role" data-role={p.role}>
                              {ROLE_LABEL[p.role] || p.role}
                            </span>
                          </td>
                          <td>{p.age}yr {p.ageDays ?? 0}d</td>
                          <td style={{ color: ratingColor(p.batRating) }}>{p.batRating}</td>
                          <td style={{ color: ratingColor(p.bowlRating) }}>{p.bowlRating}</td>
                          <td style={{ color: ratingColor(p.fldRating) }}>{p.fldRating}</td>
                          <td style={{ color: ratingColor(p.rating) }}>{p.rating}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ── Leagues Tab ── */}
        {activeTab === 'leagues' && (
          <div className="tp-leagues">
            {leaguesLoading ? (
              <div className="tp-loading">Loading leagues...</div>
            ) : !leagues || leagues.leagues.length === 0 ? (
              <div className="tp-empty">
                <HiOutlineGlobeAlt className="tp-empty-icon" />
                <p>Not in any leagues</p>
              </div>
            ) : (
              <div className="tp-league-grid">
                {leagues.leagues.map((l) => (
                  <div
                    key={l.leagueId}
                    className="tp-league-card"
                    onClick={() => navigate(`/league/${l.leagueId}`)}
                  >
                    <div className="tp-league-header">
                      <HiOutlineTrophy className="tp-league-icon" />
                      <span className="tp-league-label">{l.country} {l.format} {l.leagueLabel}</span>
                    </div>
                    <div className="tp-league-details">
                      <div className="tp-league-detail">
                        <span className="tp-league-detail-label">Division</span>
                        <span>{l.division}</span>
                      </div>
                      <div className="tp-league-detail">
                        <span className="tp-league-detail-label">Season</span>
                        <span>{l.season}</span>
                      </div>
                      <div className="tp-league-detail">
                        <span className="tp-league-detail-label">Position</span>
                        <span className="tp-league-pos">{l.position}/{l.totalTeams}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── Ground Tab ── */}
        {activeTab === 'ground' && (
          <div className="tp-ground">
            {groundLoading ? (
              <div className="tp-loading">Loading ground info...</div>
            ) : !ground ? (
              <div className="tp-empty">
                <HiOutlineBuildingOffice2 className="tp-empty-icon" />
                <p>No ground info available</p>
              </div>
            ) : (
              <div className="tp-ground-info">
                <div className="tp-ground-header-card">
                  <HiOutlineBuildingOffice2 className="tp-ground-icon" />
                  <div>
                    <h2 className="tp-ground-name">{ground.groundName || 'Home Ground'}</h2>
                    <p className="tp-ground-country">{ground.country}</p>
                  </div>
                </div>
                <div className="tp-ground-seats">
                  <h3 className="tp-ground-seats-title">Stadium Capacity</h3>
                  <div className="tp-seat-grid">
                    <div className="tp-seat-card">
                      <span className="tp-seat-label">Premium</span>
                      <span className="tp-seat-value">{ground.premium?.toLocaleString()}</span>
                    </div>
                    <div className="tp-seat-card">
                      <span className="tp-seat-label">Standard</span>
                      <span className="tp-seat-value">{ground.standard?.toLocaleString()}</span>
                    </div>
                    <div className="tp-seat-card">
                      <span className="tp-seat-label">Economy</span>
                      <span className="tp-seat-value">{ground.economy?.toLocaleString()}</span>
                    </div>
                    <div className="tp-seat-card">
                      <span className="tp-seat-label">Standing</span>
                      <span className="tp-seat-value">{ground.standing?.toLocaleString()}</span>
                    </div>
                  </div>
                  <div className="tp-seat-total">
                    <span>Total Capacity</span>
                    <span className="tp-seat-total-value">{ground.total?.toLocaleString()}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
