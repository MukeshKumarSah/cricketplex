import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  getTeamProfile,
  getTeamSquad,
  getTeamMatches,
  getTeamLeagues,
  getTeamGround,
} from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineCalendarDays,
  HiOutlineUserGroup,
  HiOutlineGlobeAlt,
  HiOutlineBuildingOffice2,
} from 'react-icons/hi2';
import './TeamProfile.css';

const TABS = [
  { key: 'matches', label: 'Matches', icon: HiOutlineCalendarDays },
  { key: 'squad', label: 'Squad', icon: HiOutlineUserGroup },
  { key: 'leagues', label: 'Leagues', icon: HiOutlineGlobeAlt },
  { key: 'ground', label: 'Ground', icon: HiOutlineBuildingOffice2 },
];

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
    if (activeTab === 'matches' && !matches) {
      setMatchesLoading(true);
      getTeamMatches(teamId)
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
              src={`http://localhost:8080/api/files/${team.teamProfilePicUrl}`}
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
            ) : !matches || matches.length === 0 ? (
              <div className="tp-empty">
                <HiOutlineCalendarDays className="tp-empty-icon" />
                <p>No matches found</p>
              </div>
            ) : (
              <div className="tp-match-list">
                {matches.map((m) => {
                  const won = m.status === 'COMPLETED' && m.winnerId === teamId;
                  const lost = m.status === 'COMPLETED' && m.winnerId && m.winnerId !== teamId;
                  const tied = m.status === 'COMPLETED' && !m.winnerId;
                  return (
                    <div
                      key={m.id}
                      className="tp-match-card tp-match-clickable"
                      onClick={() => {
                        if (m.status === 'COMPLETED' || m.status === 'IN_PROGRESS') {
                          navigate(`/match/${m.id}/scorecard`);
                        } else {
                          navigate(`/match/${m.id}/preview`);
                        }
                      }}
                    >
                      <div className="tp-match-meta">
                        <span className="tp-match-format">{m.format}</span>
                        <span className="tp-match-league">{m.leagueLabel}</span>
                        <span className="tp-match-date">{m.matchDate}</span>
                        {m.status === 'COMPLETED' && (
                          <span className={`tp-match-result ${won ? 'tp-win' : lost ? 'tp-loss' : 'tp-tie'}`}>
                            {won ? 'W' : lost ? 'L' : 'T'}
                          </span>
                        )}
                        {m.status === 'SCHEDULED' && <span className="tp-match-scheduled">Scheduled</span>}
                      </div>
                      <div className="tp-match-teams">
                        <div className="tp-match-team">
                          {m.homeTeamPicUrl ? (
                            <img className="tp-match-logo" src={`http://localhost:8080/api/files/${m.homeTeamPicUrl}`} alt="" />
                          ) : (
                            <span className="tp-match-initials">{m.homeTeamName?.slice(0, 2).toUpperCase()}</span>
                          )}
                          <span className={m.homeTeamId === teamId ? 'tp-match-team-bold' : ''}>{m.homeTeamName}</span>
                        </div>
                        <span className="tp-match-vs">vs</span>
                        <div className="tp-match-team">
                          <span className={m.awayTeamId === teamId ? 'tp-match-team-bold' : ''}>{m.awayTeamName}</span>
                          {m.awayTeamPicUrl ? (
                            <img className="tp-match-logo" src={`http://localhost:8080/api/files/${m.awayTeamPicUrl}`} alt="" />
                          ) : (
                            <span className="tp-match-initials">{m.awayTeamName?.slice(0, 2).toUpperCase()}</span>
                          )}
                        </div>
                      </div>
                      {m.resultSummary && (
                        <div className="tp-match-summary">{m.resultSummary}</div>
                      )}
                    </div>
                  );
                })}
              </div>
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
