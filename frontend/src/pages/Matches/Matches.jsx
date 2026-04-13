import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMyMatches, getCurrentSeason } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineCalendarDays,
  HiOutlineFunnel,
} from 'react-icons/hi2';
import './Matches.css';

const FORMAT_COLORS = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#34d399' };
const FORMAT_LABELS = { T20: 'T20', ODI: 'OD', FC: 'FC' };
const FRIENDLY_FORMAT_LABELS = { T20: 'T20 F', ODI: 'OD F', FC: 'FC F' };

export default function Matches() {
  const navigate = useNavigate();
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [season, setSeason] = useState(null);
  const [maxSeason, setMaxSeason] = useState(1);
  const [filterFormat, setFilterFormat] = useState(() => sessionStorage.getItem('matches_format') || '');
  const [activeTab, setActiveTab] = useState(() => sessionStorage.getItem('matches_tab') || 'upcoming');

  useEffect(() => {
    getCurrentSeason()
      .then((res) => {
        const s = res.data.season;
        const saved = sessionStorage.getItem('matches_season');
        setSeason(saved != null ? Number(saved) : s);
        setMaxSeason(s);
      })
      .catch(() => setSeason(1));
  }, []);

  useEffect(() => { if (season != null) sessionStorage.setItem('matches_season', season); }, [season]);
  useEffect(() => { sessionStorage.setItem('matches_format', filterFormat); }, [filterFormat]);
  useEffect(() => { sessionStorage.setItem('matches_tab', activeTab); }, [activeTab]);

  useEffect(() => {
    if (season == null) return;
    setLoading(true);
    getMyMatches(season, filterFormat || null)
      .then((res) => setMatches(res.data))
      .catch(() => toast.error('Failed to load matches'))
      .finally(() => setLoading(false));
  }, [season, filterFormat]);

  const today = new Date().toISOString().split('T')[0];

  const upcoming = matches.filter(
    (m) => m.status === 'IN_PROGRESS' || m.status === 'FC_DAY1_COMPLETE' || (m.matchDate >= today && (m.status === 'SCHEDULED' || m.status === 'LIVE'))
  );
  const past = matches.filter(
    (m) => m.status === 'COMPLETED'
  ).slice().reverse();

  const displayed = activeTab === 'upcoming' ? upcoming : past;

  const formatDate = (dateStr) => {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-US', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
    });
  };

  return (
    <div className="matches-page">
      {/* Header */}
      <div className="matches-header">
        <HiOutlineTrophy className="matches-header-icon" />
        <div>
          <h1>Matches</h1>
          <p className="matches-subtitle">Your upcoming and past fixtures across all leagues</p>
        </div>
      </div>

      {/* Filters */}
      <div className="matches-filters">
        <div className="matches-filter-group">
          <HiOutlineFunnel className="matches-filter-icon" />

          <div className="matches-filter-item">
            <label>Season</label>
            <select value={season ?? ''} onChange={(e) => setSeason(Number(e.target.value))}>
              {Array.from({ length: maxSeason }, (_, i) => i + 1).map((s) => (
                <option key={s} value={s}>Season {s}</option>
              ))}
            </select>
          </div>

          <div className="matches-filter-item">
            <label>Format</label>
            <select value={filterFormat} onChange={(e) => setFilterFormat(e.target.value)}>
              <option value="">All Formats</option>
              <option value="T20">T20</option>
              <option value="ODI">One Day</option>
              <option value="FC">First Class</option>
            </select>
          </div>
        </div>

        {/* Tabs */}
        <div className="matches-tabs">
          <button
            className={`matches-tab ${activeTab === 'upcoming' ? 'active' : ''}`}
            onClick={() => setActiveTab('upcoming')}
          >
            <HiOutlineCalendarDays className="matches-tab-icon" />
            Upcoming ({upcoming.length})
          </button>
          <button
            className={`matches-tab ${activeTab === 'past' ? 'active' : ''}`}
            onClick={() => setActiveTab('past')}
          >
            <HiOutlineTrophy className="matches-tab-icon" />
            Past ({past.length})
          </button>
        </div>
      </div>

      {/* Match List */}
      <div className="matches-content">
        {loading ? (
          <div className="matches-loading">Loading matches...</div>
        ) : displayed.length === 0 ? (
          <div className="matches-empty">
            {activeTab === 'upcoming'
              ? 'No upcoming matches found.'
              : 'No past matches found.'}
          </div>
        ) : (
          <div className="matches-list">
            {/* Table Header */}
            <div className="matches-table-head">
              <span className="mt-col-date">Date</span>
              <span className="mt-col-format">Format</span>
              <span className="mt-col-fixture">Fixture</span>
              <span className="mt-col-weather">Weather</span>
              <span className="mt-col-action">
                {activeTab === 'upcoming' ? 'Action' : 'Result'}
              </span>
            </div>

            {displayed.map((m) => {
              const fmtColor = FORMAT_COLORS[m.format] || '#94a3b8';
              const rowClick = () => {
                if (m.status === 'FC_DAY1_COMPLETE') {
                  navigate(`/match/${m.id}/fc-strategy`);
                } else if (m.status === 'IN_PROGRESS' || m.status === 'LIVE') {
                  navigate(`/match/${m.id}/live`);
                } else if (m.status === 'COMPLETED') {
                  navigate(`/match/${m.id}/scorecard`);
                } else {
                  navigate(`/match/${m.id}/preview`);
                }
              };
              return (
                <div key={m.id} className="matches-row matches-row-clickable" onClick={rowClick}>
                  {/* Date */}
                  <div className="mt-col-date">
                    <span className="matches-date">{formatDate(m.matchDate)}</span>
                    <span className="matches-round">
                      {m.matchType === 'FRIENDLY' ? 'Friendly' : `R${m.round} · Div ${m.leagueLabel}`}
                    </span>
                    {m.matchStartTimeUtc && <span className="matches-time">{m.matchStartTimeUtc} UTC</span>}
                  </div>

                  {/* Format */}
                  <div className="mt-col-format">
                    <span
                      className="matches-format-tag"
                      style={{
                        background: fmtColor + '18',
                        color: fmtColor,
                        borderColor: fmtColor + '40',
                      }}
                    >
                      {m.matchType === 'FRIENDLY'
                        ? (FRIENDLY_FORMAT_LABELS[m.format] || m.format + ' Friendly')
                        : (FORMAT_LABELS[m.format] || m.format)}
                    </span>
                  </div>

                  {/* Fixture */}
                  <div className="mt-col-fixture">
                    <div className="matches-fixture">
                      <div className="matches-team matches-team-home">
                        {m.homeTeamPicUrl ? (
                          <img
                            className="matches-team-logo"
                            src={`http://localhost:8080/api/files/${m.homeTeamPicUrl}`}
                            alt={m.homeTeamName}
                          />
                        ) : (
                          <span className="matches-team-initials">{m.homeTeamName?.slice(0, 2).toUpperCase()}</span>
                        )}
                        <span className={`matches-team-name ${m.isHome ? 'matches-my-team' : ''}`}>
                          {m.homeTeamName}
                        </span>
                        {m.homeIsBot && <span className="matches-bot-badge">BOT</span>}
                      </div>
                      <span className="matches-vs">vs</span>
                      <div className="matches-team matches-team-away">
                        {m.awayIsBot && <span className="matches-bot-badge">BOT</span>}
                        <span className={`matches-team-name ${!m.isHome ? 'matches-my-team' : ''}`}>
                          {m.awayTeamName}
                        </span>
                        {m.awayTeamPicUrl ? (
                          <img
                            className="matches-team-logo"
                            src={`http://localhost:8080/api/files/${m.awayTeamPicUrl}`}
                            alt={m.awayTeamName}
                          />
                        ) : (
                          <span className="matches-team-initials">{m.awayTeamName?.slice(0, 2).toUpperCase()}</span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Weather */}
                  <div className="mt-col-weather">
                    {m.weather?.available ? (
                      <div className="matches-weather">
                        <span className="matches-weather-icon">{m.weather.icon}</span>
                        <div className="matches-weather-info">
                          <span className="matches-weather-condition">{m.weather.condition}</span>
                          <span className="matches-weather-temp">{m.weather.temperature}°C · {m.weather.humidity}%</span>
                        </div>
                      </div>
                    ) : (
                      <span className="matches-weather-na">Forecast N/A</span>
                    )}
                  </div>

                  {/* Action / Result */}
                  <div className="mt-col-action">
                    {activeTab === 'upcoming' ? (
                      m.status === 'FC_DAY1_COMPLETE' ? (
                        <button className="matches-action-btn matches-live-btn"
                          onClick={(e) => { e.stopPropagation(); navigate(`/match/${m.id}/fc-strategy`); }}>
                          Day 2 Strategy
                        </button>
                      ) : (m.status === 'LIVE' || m.status === 'IN_PROGRESS') ? (
                        <button className="matches-action-btn matches-live-btn"
                          onClick={(e) => { e.stopPropagation(); navigate(`/match/${m.id}/live`); }}>
                          View Live
                        </button>
                      ) : (
                        <button
                          className="matches-action-btn matches-lineup-btn"
                          onClick={(e) => { e.stopPropagation(); navigate(`/match/${m.id}/lineup`); }}
                        >
                          {m.lineupSet && <span className="matches-tick">✓</span>}
                          Lineup Setup
                        </button>
                      )
                    ) : (
                      <span className="matches-result">
                        {m.status === 'COMPLETED' ? 'Completed' : m.status}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
