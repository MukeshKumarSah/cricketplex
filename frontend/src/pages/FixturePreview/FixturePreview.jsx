import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getFixturePreview } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineCalendarDays,
  HiOutlineMapPin,
  HiOutlineTrophy,
  HiOutlineClipboardDocumentList,
} from 'react-icons/hi2';
import './FixturePreview.css';

const PITCH_META = {
  STANDARD: { label: 'Standard', desc: 'Balanced conditions for both bat and ball' },
  DUSTY:    { label: 'Dusty', desc: 'Favours spinners, turns sharply later in the game' },
  GREEN:    { label: 'Green', desc: 'Seam movement for pace bowlers, especially early on' },
  FLAT:     { label: 'Flat', desc: 'Batting paradise, very little help for bowlers' },
  UNEVEN:   { label: 'Uneven', desc: 'Variable bounce, unpredictable for batsmen' },
  DRY:      { label: 'Dry', desc: 'Low and slow, spin becomes increasingly effective' },
  SLOW:     { label: 'Slow', desc: 'Pace is taken off, hard to score quickly' },
  BOUNCY:   { label: 'Bouncy', desc: 'Extra bounce for fast bowlers, tests technique' },
};

export default function FixturePreview() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getFixturePreview(fixtureId)
      .then((res) => setData(res.data))
      .catch(() => toast.error('Failed to load fixture details'))
      .finally(() => setLoading(false));
  }, [fixtureId]);

  if (loading) {
    return (
      <div className="fp-page">
        <div className="fp-loading">Loading fixture...</div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="fp-page">
        <div className="fp-loading">Fixture not found.</div>
      </div>
    );
  }

  const home = data.homeTeam;
  const away = data.awayTeam;
  const rivalry = data.rivalry;
  const weather = data.weather;
  const pitch = PITCH_META[data.pitchType] || PITCH_META.STANDARD;

  return (
    <div className="fp-page">
      {/* Match Header */}
      <div className="fp-header">
        <div className="fp-header-meta">
          <span className="fp-format-badge">{data.format}</span>
          {data.leagueLabel && (
            <span
              className="fp-league-link"
              onClick={() => navigate(`/league/${data.leagueId}`)}
            >
              {data.leagueLabel}
            </span>
          )}
          {!data.leagueLabel && <span className="fp-league-link">Friendly</span>}
          {data.round > 0 && <span className="fp-round">Round {data.round}</span>}
        </div>

        <div className="fp-teams-row">
          <div className="fp-team fp-team-home" onClick={() => navigate(`/team/${home.id}`)}>
            {home.teamProfilePicUrl ? (
              <img className="fp-team-logo" src={`http://localhost:8080/api/files/${home.teamProfilePicUrl}`} alt="" />
            ) : (
              <span className="fp-team-initials">{home.teamName?.slice(0, 2).toUpperCase()}</span>
            )}
            <span className="fp-team-name">{home.teamName}</span>
            {home.isBot && <span className="fp-bot">BOT</span>}
          </div>

          <div className="fp-vs-block">
            <span className="fp-vs">VS</span>
            <span className="fp-status-tag" data-status={data.status}>
              {data.status === 'COMPLETED' ? 'Completed' : data.status === 'IN_PROGRESS' ? 'LIVE' : data.status === 'FC_DAY1_COMPLETE' ? 'Day 1 Done' : 'Upcoming'}
            </span>
          </div>

          <div className="fp-team fp-team-away" onClick={() => navigate(`/team/${away.id}`)}>
            {away.teamProfilePicUrl ? (
              <img className="fp-team-logo" src={`http://localhost:8080/api/files/${away.teamProfilePicUrl}`} alt="" />
            ) : (
              <span className="fp-team-initials">{away.teamName?.slice(0, 2).toUpperCase()}</span>
            )}
            <span className="fp-team-name">{away.teamName}</span>
            {away.isBot && <span className="fp-bot">BOT</span>}
          </div>
        </div>

        <div className="fp-header-details">
          <span><HiOutlineCalendarDays /> {data.matchDate}{data.matchStartTimeUtc && ` · ${data.matchStartTimeUtc} UTC`}</span>
          <span><HiOutlineMapPin /> {home.groundName || 'Home Ground'}, {home.country}</span>
        </div>
      </div>

      {/* Info Cards */}
      <div className="fp-cards">
        {/* Pitch */}
        <div className="fp-card">
          <h3 className="fp-card-title">
            <HiOutlineClipboardDocumentList className="fp-card-icon" />
            Pitch Conditions
          </h3>
          <div className="fp-pitch">
            <span className="fp-pitch-type">{pitch.label}</span>
            <p className="fp-pitch-desc">{pitch.desc}</p>
          </div>
        </div>

        {/* Weather */}
        <div className="fp-card">
          <h3 className="fp-card-title">
            Weather
          </h3>
          {weather && weather.available ? (
            <div className="fp-weather">
              <span className="fp-weather-icon">{weather.icon}</span>
              <div className="fp-weather-details">
                <span className="fp-weather-condition">{weather.condition}</span>
                <span className="fp-weather-temp">{weather.temperature}°C · Humidity {weather.humidity}%</span>
              </div>
            </div>
          ) : (
            <p className="fp-weather-unavailable">{weather?.message || 'Weather not available'}</p>
          )}
        </div>
      </div>

      {/* Rivalry */}
      <div className="fp-card fp-rivalry-card">
        <h3 className="fp-card-title">
          <HiOutlineTrophy className="fp-card-icon" />
          Head to Head
        </h3>
        {rivalry.totalMatches === 0 ? (
          <p className="fp-no-rivalry">No previous encounters between these teams</p>
        ) : (
          <>
            <div className="fp-rivalry-summary">
              <div className="fp-rival-stat fp-rival-home">
                <span className="fp-rival-count">{rivalry.homeWins}</span>
                <span className="fp-rival-label">{home.teamName} Wins</span>
              </div>
              <div className="fp-rival-stat fp-rival-draw">
                <span className="fp-rival-count">{rivalry.ties}</span>
                <span className="fp-rival-label">Ties</span>
              </div>
              <div className="fp-rival-stat fp-rival-away">
                <span className="fp-rival-count">{rivalry.awayWins}</span>
                <span className="fp-rival-label">{away.teamName} Wins</span>
              </div>
            </div>
            <div className="fp-rival-total">
              {rivalry.totalMatches} match{rivalry.totalMatches !== 1 ? 'es' : ''} played
            </div>
            {rivalry.recentMatches.length > 0 && (
              <div className="fp-recent">
                <h4 className="fp-recent-title">Recent Results</h4>
                {rivalry.recentMatches.map((m, i) => (
                  <div key={i} className="fp-recent-row">
                    <span className="fp-recent-date">{m.date}</span>
                    <span className="fp-recent-format">{m.format}</span>
                    <span className="fp-recent-summary">{m.summary}</span>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {/* Action Buttons */}
      <div className="fp-actions">
        {data.status === 'COMPLETED' && (
          <button className="fp-btn fp-btn-primary" onClick={() => navigate(`/match/${fixtureId}/scorecard`)}>
            View Scorecard
          </button>
        )}
        {data.status === 'IN_PROGRESS' && (
          <button className="fp-btn fp-btn-primary" onClick={() => navigate(`/match/${fixtureId}/live`)}>
            Watch Live
          </button>
        )}
        {data.status === 'FC_DAY1_COMPLETE' && (
          <>
            <button className="fp-btn fp-btn-primary" onClick={() => navigate(`/match/${fixtureId}/scorecard`)}>
              View Day 1 Scorecard
            </button>
            {data.isUserInvolved && (
              <button className="fp-btn fp-btn-accent" onClick={() => navigate(`/match/${fixtureId}/fc-strategy`)}>
                Update Day 2 Strategy
              </button>
            )}
          </>
        )}
        {data.isUserInvolved && data.status === 'SCHEDULED' && (
          <button className="fp-btn fp-btn-accent" onClick={() => navigate(`/match/${fixtureId}/lineup`)}>
            {data.lineupSet ? 'Edit Lineup' : 'Set Lineup'}
          </button>
        )}
      </div>
    </div>
  );
}
