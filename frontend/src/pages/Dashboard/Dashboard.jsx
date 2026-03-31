import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getSettings, getStadiumSeats, getMyLeagues, getWeatherForecast } from '../../api/auth';
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
  country: 'India',
  countryFlag: '🇮🇳',
  ground: {
    name: 'CricketPlex Arena',
    seats: 25000,
  },
  teamMorale: 78,
  teamFans: 142500,
  trophies: [
    { name: 'T20 Cup 2025', icon: '🏆', year: 2025 },
    { name: 'OD Shield Runner-up', icon: '🥈', year: 2025 },
    { name: 'FC League Winner', icon: '🏆', year: 2024 },
  ],
  recentActivities: [
    {
      type: 'match-won',
      text: 'Won T20 match against Thunder Hawks by 24 runs',
      time: '2 hours ago',
    },
    {
      type: 'revenue',
      text: 'Match revenue $45,000 received for T20 match against Thunder Hawks',
      time: '2 hours ago',
    },
    {
      type: 'bought',
      text: 'Bought James Patterson from Royal Strikers at $120K',
      time: '5 hours ago',
    },
    {
      type: 'lineup',
      text: 'Set lineup for OD match against Dragon XI',
      time: '8 hours ago',
    },
    {
      type: 'training',
      text: 'Training Completed — Batting session finished',
      time: '10 hours ago',
    },
    {
      type: 'academy',
      text: 'Recruited Rahul Mehta from Academy',
      time: '1 day ago',
    },
    {
      type: 'listed',
      text: 'Listed David Warner on Transfer Market',
      time: '1 day ago',
    },
    {
      type: 'match-lost',
      text: 'Lost OD match against Royal Strikers by 3 wickets',
      time: '2 days ago',
    },
    {
      type: 'sold',
      text: 'Sold Marcus Hill to Falcon CC for $85K',
      time: '2 days ago',
    },
    {
      type: 'ground',
      text: 'Ground Update — New floodlights installed',
      time: '3 days ago',
    },
    {
      type: 'failed-sale',
      text: 'Player Tom Brady failed to sell — relisted',
      time: '3 days ago',
    },
    {
      type: 'retired',
      text: 'Retired player Andrew Symonds — legend farewell',
      time: '4 days ago',
    },
    {
      type: 'released',
      text: 'Released player Jake Morrison from squad',
      time: '4 days ago',
    },
    {
      type: 'match-won',
      text: 'Won FC match against Shield Warriors by an innings and 34 runs',
      time: '5 days ago',
    },
    {
      type: 'revenue',
      text: 'Match revenue $62,000 received for FC match against Shield Warriors',
      time: '5 days ago',
    },
    {
      type: 'bought',
      text: 'Bought Liam Scott from Coastal XI at $200K',
      time: '6 days ago',
    },
    {
      type: 'training',
      text: 'Training Completed — Bowling camp finished',
      time: '7 days ago',
    },
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
    default: return 'Event';
  }
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
                stroke={getMoraleColor(d.teamMorale)}
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={`${(d.teamMorale / 100) * 327} 327`}
                transform="rotate(-90 60 60)"
              />
            </svg>
            <div className="dash-morale-ring-center">
              <span className="dash-morale-percent">{d.teamMorale}%</span>
              <span className="dash-morale-label" style={{ color: getMoraleColor(d.teamMorale) }}>
                {getMoraleLabel(d.teamMorale)}
              </span>
            </div>
          </div>
          <div className="dash-morale-breakdown">
            <div className="morale-factor">
              <span>Match Results</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: '82%', background: '#34d399' }} /></div>
            </div>
            <div className="morale-factor">
              <span>Squad Depth</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: '65%', background: '#fbbf24' }} /></div>
            </div>
            <div className="morale-factor">
              <span>Facilities</span>
              <div className="morale-bar"><div className="morale-bar-fill" style={{ width: '90%', background: '#22d3ee' }} /></div>
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
            <span className="dash-fans-number">{d.teamFans.toLocaleString()}</span>
          </div>
          <div className="dash-fans-growth">
            <HiOutlineBolt className="dash-fans-bolt" />
            <span>+2,340 this week</span>
          </div>
          <div className="dash-fans-bar-wrapper">
            <div className="dash-fans-bar-track">
              <div className="dash-fans-bar-fill" style={{ width: '57%' }} />
            </div>
            <div className="dash-fans-bar-labels">
              <span>0</span>
              <span>250K target</span>
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
          <span className="dash-activities-count">{d.recentActivities.length} events</span>
        </div>
        {(() => {
          const MAX_ITEMS = 15;
          const total = d.recentActivities.length;
          const showItems = d.recentActivities.slice(0, MAX_ITEMS);

          return (
            <>
              <div className="dash-activities-list">
                {showItems.map((a, i) => (
                  <div className="dash-activity-item" key={i}>
                    <div className="dash-activity-icon">{activityIcon(a.type)}</div>
                    <div className="dash-activity-content">
                      <div className="dash-activity-top">
                        <span
                          className="dash-activity-tag"
                          style={{ background: activityTagColor(a.type) + '20', color: activityTagColor(a.type) }}
                        >
                          {activityTagLabel(a.type)}
                        </span>
                        <span className="dash-activity-time">{a.time}</span>
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
