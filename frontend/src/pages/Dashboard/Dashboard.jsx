import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getSettings, getStadiumSeats, getMyLeagues, getWeatherForecast, getRecentActivities, getDashboardStats, getMyMatches, getAttendanceHistory } from '../../api/auth';
import {
  HiOutlineTrophy,
  HiOutlineGlobeAlt,
  HiOutlineBuildingOffice2,
  HiOutlineUserGroup,
  HiOutlineHeart,
  HiOutlineFire,
  HiOutlineBolt,
  HiOutlineClock,
  HiOutlineCalendarDays,
  HiOutlineSparkles,
} from 'react-icons/hi2';
import './Dashboard.css';

// ── Mock Data (will come from backend later) ──
const mockData = {
  lastActive: 'Currently Active',
  isActive: true,
  trophies: [
    { name: 'T20 Cup 2025', icon: '🏆', year: 2025 },
    { name: 'OD Shield Runner-up', icon: '🥈', year: 2025 },
    { name: 'FC League Winner', icon: '🏆', year: 2024 },
  ],
};

const activityIcon = (type) => {
  switch (type) {
    case 'match-won': return '🏏';
    case 'match-lost': return '😞';
    case 'bought': return '🛒';
    case 'sold': return '💸';
    case 'listed': return '📋';
    case 'failed-sale': return '❌';
    case 'academy': return '🎓';
    case 'lineup': return '📝';
    case 'revenue': return '💰';
    case 'ground': return '🏟️';
    case 'training': return '🏋️';
    case 'retired': return '👋';
    case 'released': return '🚪';
    case 'signup': return '🎉';
    case 'team-setup': return '🏗️';
    case 'league': return '🏆';
    default: return '📌';
  }
};

const activityTagColor = (type) => {
  switch (type) {
    case 'match-won': return '#22c55e';
    case 'match-lost': return '#ef4444';
    case 'bought': return '#22d3ee';
    case 'sold': return '#fbbf24';
    case 'listed': return '#a78bfa';
    case 'failed-sale': return '#f87171';
    case 'academy': return '#34d399';
    case 'lineup': return '#60a5fa';
    case 'revenue': return '#fbbf24';
    case 'ground': return '#fb923c';
    case 'training': return '#818cf8';
    case 'retired': return '#94a3b8';
    case 'released': return '#94a3b8';
    case 'signup': return '#22d3ee';
    case 'team-setup': return '#22d3ee';
    case 'league': return '#a78bfa';
    default: return '#64748b';
  }
};

const activityTagLabel = (type) => {
  switch (type) {
    case 'match-won': return 'Won';
    case 'match-lost': return 'Lost';
    case 'bought': return 'Bought';
    case 'sold': return 'Sold';
    case 'listed': return 'Listed';
    case 'failed-sale': return 'Failed';
    case 'academy': return 'Academy';
    case 'lineup': return 'Lineup';
    case 'revenue': return 'Revenue';
    case 'ground': return 'Ground';
    case 'training': return 'Training';
    case 'retired': return 'Retired';
    case 'released': return 'Released';
    case 'signup': return 'Welcome';
    case 'team-setup': return 'Setup';
    case 'league': return 'League';
    default: return 'Event';
  }
};

const timeAgo = (dateStr) => {
  const now = new Date();
  const past = new Date(dateStr);
  const diff = Math.floor((now - past) / 1000);
  if (diff < 60) return 'just now';
  if (diff < 3600) return Math.floor(diff / 60) + 'm ago';
  if (diff < 86400) return Math.floor(diff / 3600) + 'h ago';
  if (diff < 604800) return Math.floor(diff / 86400) + 'd ago';
  return past.toLocaleDateString();
};

const formatFans = (n) => {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
  return n.toString();
};

const getMoraleLabel = (m) => {
  if (m >= 90) return 'Ecstatic';
  if (m >= 75) return 'Happy';
  if (m >= 55) return 'Content';
  if (m >= 35) return 'Unsettled';
  return 'Low';
};

const getMoraleColor = (m) => {
  if (m >= 90) return '#22d3ee';
  if (m >= 75) return '#34d399';
  if (m >= 55) return '#fbbf24';
  if (m >= 35) return '#fb923c';
  return '#f87171';
};

export default function Dashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const d = mockData;
  const [team, setTeam] = useState(null);
  const [totalSeats, setTotalSeats] = useState(0);
  const [myLeagues, setMyLeagues] = useState([]);
  const [weatherForecast, setWeatherForecast] = useState(null);
  const [weatherCountry, setWeatherCountry] = useState('');
  const [activities, setActivities] = useState([]);
  const [dashStats, setDashStats] = useState(null);
  const [dashMatches, setDashMatches] = useState([]);
  const [attendanceHistory, setAttendanceHistory] = useState([]);

  useEffect(() => {
    getSettings()
      .then((res) => setTeam(res.data.team))
      .catch(() => {});
    getStadiumSeats()
      .then((res) => {
        setTotalSeats(res.data.total || 0);
      })
      .catch(() => {});
    getMyLeagues()
      .then((res) => setMyLeagues(res.data.leagues || []))
      .catch(() => {});
    getWeatherForecast()
      .then((res) => {
        setWeatherForecast(res.data.forecast || []);
        setWeatherCountry(res.data.country || '');
      })
      .catch(() => {});
    getRecentActivities()
      .then((res) => setActivities(res.data || []))
      .catch(() => {});
    getDashboardStats()
      .then((res) => setDashStats(res.data))
      .catch(() => {});
    getAttendanceHistory()
      .then((res) => setAttendanceHistory(res.data || []))
      .catch(() => {});
    getMyMatches()
      .then((res) => {
        const all = res.data || [];
        const completed = all.filter((m) => m.status === 'COMPLETED');
        const live = all.filter((m) => m.status === 'IN_PROGRESS' || m.status === 'LIVE');
        const upcoming = all.filter((m) => m.status === 'SCHEDULED');
        // Smart pick: 2 completed + 1 live + 2 upcoming (adjust based on availability)
        let picked = [];
        if (live.length > 0) {
          const c = completed.slice(-2);
          const l = live.slice(0, 1);
          const need = 5 - c.length - l.length;
          const u = upcoming.slice(0, Math.max(need, 0));
          picked = [...c, ...l, ...u];
        } else if (completed.length > 0) {
          const c = completed.slice(-2);
          const need = 5 - c.length;
          const u = upcoming.slice(0, Math.max(need, 0));
          picked = [...c, ...u];
        } else {
          picked = upcoming.slice(0, 5);
        }
        setDashMatches(picked.slice(0, 5));
      })
      .catch(() => {});
  }, []);

  const managerInitials = user?.name
    ? user.name.split(' ').map((n) => n[0]).join('').toUpperCase().slice(0, 2)
    : '??';

  const teamInitials = team?.teamName
    ? team.teamName.slice(0, 2).toUpperCase()
    : '??';

  return (
    <div className="dashboard">
      {/* ── Team Header Card ── */}
      <section className="dash-header-card">
        <div className="dash-header-left">
          {/* Team Logo — Primary */}
          <div className="dash-team-badge">
            {team?.teamProfilePicUrl ? (
              <img src={team.teamProfilePicUrl} alt="Team" className="dash-team-logo" />
            ) : (
              <span className="dash-team-initials">{teamInitials}</span>
            )}
            <div className={`dash-status-dot ${d.isActive ? 'active' : ''}`} />
          </div>

          <div className="dash-header-info">
            <h1 className="dash-team-name-main">{team?.teamName || 'My Team'}</h1>

            {/* Manager row: avatar + manager name */}
            <div className="dash-manager-row">
              <div className="dash-manager-mini-badge">
                {user?.profilePicUrl ? (
                  <img src={user.profilePicUrl} alt="Manager" className="dash-manager-mini-avatar" />
                ) : (
                  <span className="dash-manager-mini-initials">{managerInitials}</span>
                )}
              </div>
              <span className="dash-manager-label">{user?.name || 'Manager'}</span>
              <span className="dash-manager-role">Manager</span>
            </div>

            <div className="dash-header-meta">
              <span className="dash-meta-item">
                <HiOutlineClock />
                {d.lastActive}
              </span>
              <span className="dash-meta-divider">•</span>
              <span className="dash-meta-item">
                <HiOutlineGlobeAlt />
                {team?.country || 'Unknown'}
              </span>
            </div>
          </div>
        </div>
        <div className="dash-header-stats">
          <div className="dash-quick-stat">
            <span className="dash-quick-stat-value">{totalSeats.toLocaleString()}</span>
            <span className="dash-quick-stat-label">Stadium Seats</span>
          </div>
          <div className="dash-quick-stat">
            <span className="dash-quick-stat-value">{formatFans(team?.fans || 0)}</span>
            <span className="dash-quick-stat-label">Total Fans</span>
          </div>
          <div className="dash-quick-stat">
            <span className="dash-quick-stat-value">{d.trophies.length}</span>
            <span className="dash-quick-stat-label">Trophies</span>
          </div>
        </div>
      </section>

      {/* ── Weather Forecast ── */}
      {weatherForecast && weatherForecast.length > 0 && (
        <section className="dash-card dash-weather-card">
          <div className="dash-card-header">
            <span className="dash-card-icon">🌤️</span>
            <h2>{weatherCountry} — 5-Day Forecast</h2>
          </div>
          <div className="dash-weather-grid">
            {weatherForecast.map((day, i) => {
              const dt = new Date(day.date + 'T00:00:00');
              const label = i === 0 ? 'Today' : dt.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
              return (
                <div key={day.date} className="dash-weather-day">
                  <span className="dash-weather-date">{label}</span>
                  <span className="dash-weather-icon">{day.icon}</span>
                  <span className="dash-weather-cond">{day.condition}</span>
                  <span className="dash-weather-temp">{day.temperature}°C</span>
                  <span className="dash-weather-humidity">{day.humidity}% humidity</span>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* ── Ground Info ── */}
      {/* ── Home Ground ── */}
      <section className="dash-card dash-ground-card">
        <div className="dash-card-header">
          <HiOutlineBuildingOffice2 className="dash-card-icon" />
          <h2>Home Ground</h2>
        </div>
        <div className="dash-ground-info">
          <div className="dash-ground-visual">
            <div className="dash-ground-pitch">
              <div className="dash-ground-pitch-strip" />
              <div className="dash-ground-crease left" />
              <div className="dash-ground-crease right" />
            </div>
          </div>
          <div className="dash-ground-details">
            <h3 className="dash-ground-name">{team?.groundName || 'Home Ground'}</h3>
            <p className="dash-ground-seats">
              <HiOutlineUserGroup /> {totalSeats.toLocaleString()} seats
            </p>
            <p className="dash-ground-country">
              <HiOutlineGlobeAlt /> {team?.country || 'Unknown'}
            </p>
          </div>
        </div>

        {attendanceHistory.length > 0 && (
          <div className="dash-attendance">
            <h4 className="dash-attendance-title">Recent Home Attendance</h4>
            {attendanceHistory.map((a, i) => (
              <div key={i} className="dash-attendance-row">
                <span className="dash-attendance-opponent">vs {a.opponent}</span>
                <span className="dash-attendance-format">{a.format}</span>
                <span className="dash-attendance-count">{a.attendance?.toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ── Upcoming Matches ── */}
      <section className="dash-card dash-matches-card">
        <div className="dash-card-header">
          <HiOutlineCalendarDays className="dash-card-icon" />
          <h2>Matches</h2>
          <span className="dash-matches-view-all" onClick={() => navigate('/matches')}>View All</span>
        </div>
        <div className="dash-matches-list">
          {dashMatches.length > 0 ? dashMatches.map((m) => {
            const isLive = m.status === 'IN_PROGRESS' || m.status === 'LIVE';
            const isDone = m.status === 'COMPLETED';
            const statusClass = isLive ? 'live' : isDone ? 'completed' : 'upcoming';
            const statusLabel = isLive ? 'LIVE' : isDone ? 'Completed' : 'Upcoming';
            const formatColors = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#34d399' };
            return (
              <div
                key={m.id}
                className={`dash-match-row dash-match-${statusClass}`}
                onClick={() => navigate(isLive ? `/match/${m.id}/live` : isDone ? `/match/${m.id}/scorecard` : `/match/${m.id}/preview`)}
              >
                <div className="dash-match-date">
                  {m.matchDate ? new Date(m.matchDate + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : '—'}
                </div>
                <div className="dash-match-format" style={{ background: formatColors[m.format] || '#64748b' }}>
                  {m.format}
                </div>
                <div className="dash-match-teams">
                  <span className={m.isHome ? 'dash-match-my-team' : ''}>{m.homeTeamName}</span>
                  <span className="dash-match-vs">vs</span>
                  <span className={!m.isHome ? 'dash-match-my-team' : ''}>{m.awayTeamName}</span>
                </div>
                <span className={`dash-match-status ${statusClass}`}>{statusLabel}</span>
              </div>
            );
          }) : (
            <div className="dash-empty-state">
              <span className="dash-empty-icon">🏏</span>
              <p>No matches scheduled yet.</p>
            </div>
          )}
        </div>
      </section>

      {/* ── League Standings ── */}
      <section className="dash-card dash-leagues-card">
        <div className="dash-card-header">
          <HiOutlineTrophy className="dash-card-icon" />
          <h2>League Standings</h2>
        </div>
        <div className="dash-leagues">
          {myLeagues.length > 0 ? (
            myLeagues.map((lg) => {
              const formatColors = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#34d399' };
              const formatLabels = { T20: 'T20', ODI: 'One Day', FC: 'First Class' };
              return (
                <div
                  className="dash-league-row dash-league-row-clickable"
                  key={lg.leagueId}
                  onClick={() => navigate(`/league/${lg.leagueId}`)}
                >
                  <div className="dash-league-badge" style={{ background: formatColors[lg.format] || '#64748b' }}>
                    {formatLabels[lg.format] || lg.format}
                  </div>
                  <div className="dash-league-info">
                    <div className="dash-league-name">
                      {lg.country} {lg.format} {lg.leagueLabel}
                    </div>
                    <div className="dash-league-division">
                      Division {lg.division} · League {lg.leagueNumber} · Season {lg.season}
                    </div>
                  </div>
                  <div className="dash-league-form">
                    {Array.from({ length: 5 }, (_, i) => {
                      const r = lg.recentForm?.[i];
                      return (
                        <span
                          key={i}
                          className={`dash-form-dot ${r === 'W' ? 'win' : r === 'L' ? 'loss' : r === 'T' ? 'tie' : 'empty'}`}
                        >
                          {r || '.'}
                        </span>
                      );
                    })}
                  </div>
                  <div className="dash-league-position">
                    <span className="dash-league-pos-num">#{lg.position}</span>
                    <span className="dash-league-pos-label">of {lg.totalTeams}</span>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="dash-empty-state">
              <span className="dash-empty-icon">🏆</span>
              <p>No league assignments yet.</p>
            </div>
          )}
        </div>
      </section>

      {/* ── Team Morale & Fans ── */}
      <section className="dash-card dash-morale-card">
        <div className="dash-card-header">
          <HiOutlineFire className="dash-card-icon" />
          <h2>Team Morale</h2>
        </div>
        <div className="dash-morale-content">
          <div className="dash-morale-ring-wrapper">
            <svg className="dash-morale-ring" viewBox="0 0 120 120">
              <circle cx="60" cy="60" r="52" fill="none" stroke="#1e293b" strokeWidth="10" />
              <circle
                cx="60" cy="60" r="52"
                fill="none"
                stroke={getMoraleColor(dashStats?.morale ?? 50)}
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={`${((dashStats?.morale ?? 50) / 100) * 327} 327`}
                transform="rotate(-90 60 60)"
              />
            </svg>
            <div className="dash-morale-ring-center">
              <span className="dash-morale-percent">{dashStats?.morale ?? '–'}%</span>
              <span className="dash-morale-label" style={{ color: getMoraleColor(dashStats?.morale ?? 50) }}>
                {getMoraleLabel(dashStats?.morale ?? 50)}
              </span>
            </div>
          </div>
          <div className="dash-morale-breakdown">
            <div className="morale-factor">
              <span>Recent Form ({dashStats?.recentWins ?? 0}W {dashStats?.recentDraws ?? 0}D {dashStats?.recentLosses ?? 0}L)</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: `${dashStats?.morale ?? 50}%`, background: '#34d399' }} /></div>
            </div>
            <div className="morale-factor">
              <span>Squad Confidence</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: `${dashStats?.squadConfidenceFactor ?? 50}%`, background: '#fbbf24' }} /></div>
            </div>
            <div className="morale-factor">
              <span>Facilities (Lv {team?.academyLevel ?? 1})</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: `${dashStats?.facilityFactor ?? 25}%`, background: '#22d3ee' }} /></div>
            </div>
          </div>
        </div>
      </section>

      <section className="dash-card dash-fans-card">
        <div className="dash-card-header">
          <HiOutlineHeart className="dash-card-icon" />
          <h2>Team Fans</h2>
        </div>
        <div className="dash-fans-content">
          <div className="dash-fans-count">
            <HiOutlineSparkles className="dash-fans-sparkle" />
            <span className="dash-fans-number">{(dashStats?.fans ?? team?.fans ?? 0).toLocaleString()}</span>
          </div>
          <div className="dash-fans-growth">
            <HiOutlineBolt className="dash-fans-bolt" />
            <span style={{ color: '#94a3b8' }}>
              Recent: {dashStats?.recentWins ?? 0}W {dashStats?.recentDraws ?? 0}D {dashStats?.recentLosses ?? 0}L ({dashStats?.recentMatchCount ?? 0} matches)
            </span>
          </div>
          <div className="dash-fans-bar-wrapper">
            <div className="dash-fans-bar-track">
              <div className="dash-fans-bar-fill" style={{ width: `${Math.min(100, ((dashStats?.fans ?? team?.fans ?? 0) / (dashStats?.fanCeiling ?? 150000)) * 100)}%` }} />
            </div>
            <div className="dash-fans-bar-labels">
              <span>0</span>
              <span>{formatFans(dashStats?.fanCeiling ?? 150000)} ceiling</span>
            </div>
          </div>
        </div>
      </section>

      {/* ── Trophy Cabinet ── */}
      <section className="dash-card dash-trophies-card">
        <div className="dash-card-header">
          <HiOutlineTrophy className="dash-card-icon gold" />
          <h2>Trophy Cabinet</h2>
        </div>
        {d.trophies.length > 0 ? (
          <div className="dash-trophies-grid">
            {d.trophies.map((t, i) => (
              <div className="dash-trophy-item" key={i}>
                <div className="dash-trophy-icon">{t.icon}</div>
                <div className="dash-trophy-info">
                  <span className="dash-trophy-name">{t.name}</span>
                  <span className="dash-trophy-year">{t.year}</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="dash-empty-state">
            <span className="dash-empty-icon">🏆</span>
            <p>No trophies yet. Win a league to fill your cabinet!</p>
          </div>
        )}
      </section>

      {/* ── Recent Activities ── */}
      <section className="dash-card dash-activities-card">
        <div className="dash-card-header">
          <HiOutlineCalendarDays className="dash-card-icon" />
          <h2>Recent Activities</h2>
          <span className="dash-activities-count">{activities.length} events</span>
        </div>
        {(() => {
          const MAX_ITEMS = 15;
          const total = activities.length;
          const showItems = activities.slice(0, MAX_ITEMS);

          return (
            <>
              <div className="dash-activities-list">
                {showItems.length === 0 && (
                  <div className="dash-empty-state">
                    <span className="dash-empty-icon">📋</span>
                    <p>No activities yet. Start playing to see your history!</p>
                  </div>
                )}
                {showItems.map((a, i) => (
                  <div className="dash-activity-item" key={a.id || i}>
                    <div className="dash-activity-icon">{activityIcon(a.type)}</div>
                    <div className="dash-activity-content">
                      <div className="dash-activity-top">
                        <span
                          className="dash-activity-tag"
                          style={{ background: activityTagColor(a.type) + '20', color: activityTagColor(a.type) }}
                        >
                          {activityTagLabel(a.type)}
                        </span>
                        <span className="dash-activity-time">{timeAgo(a.createdAt)}</span>
                      </div>
                      <p className="dash-activity-text">{a.text}</p>
                    </div>
                  </div>
                ))}
              </div>
              {total > MAX_ITEMS && (
                <div className="dash-activities-footer">
                  <button className="dash-activities-more-btn">
                    View All Activities →
                  </button>
                </div>
              )}
            </>
          );
        })()}
      </section>
    </div>
  );
}
