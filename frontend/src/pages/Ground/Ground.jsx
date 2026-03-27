
import { useState, useEffect } from 'react';
import { format, parseISO } from 'date-fns';
import toast from 'react-hot-toast';
import {
  HiOutlineBuildingOffice2,
  HiOutlineMinus,
  HiOutlinePlus,
} from 'react-icons/hi2';
import {
  getStadiumSeats,
  updateStadiumSeats,
  getUpcomingHomeMatches,
  updateMatchPitch,
} from '../../api/auth';
import './Ground.css';

const seatTypes = [
  { key: 'premium', label: 'Premium', color: '#fbbf24', desc: 'Best view, padded seats, VIP access', icon: '👑' },
  { key: 'standard', label: 'Standard', color: '#38bdf8', desc: 'Covered stands, comfortable seating', icon: '💺' },
  { key: 'economy', label: 'Economy', color: '#a3e635', desc: 'Open stands, basic seating', icon: '🪑' },
  { key: 'standing', label: 'Standing', color: '#f472b6', desc: 'Standing area, most affordable', icon: '🧍' },
];

const pitchOptions = [
  { key: 'STANDARD', label: 'Standard', desc: 'Balanced for bat and ball', icon: '⚖️' },
  { key: 'DUSTY', label: 'Dusty', desc: 'Helps spinners, low bounce', icon: '🌪️' },
  { key: 'GREEN', label: 'Green', desc: 'Assists seamers, extra movement', icon: '🟢' },
  { key: 'FLAT', label: 'Flat', desc: 'Batting paradise, high scores', icon: '🏏' },
  { key: 'UNEVEN', label: 'Uneven', desc: 'Unpredictable bounce, tough for batters', icon: '📈' },
  { key: 'DRY', label: 'Dry', desc: 'Cracks up, helps spin late', icon: '☀️' },
  { key: 'SLOW', label: 'Slow', desc: 'Low pace, hard to score quickly', icon: '🐢' },
  { key: 'BOUNCY', label: 'Bouncy', desc: 'Extra bounce, suits fast bowlers', icon: '🏀' },
];

const formatBadge = (fmt) => {
  const map = { T20: '#22d3ee', ODI: '#a3e635', FC: '#fbbf24' };
  return { color: map[fmt] || '#94a3b8', label: fmt };
};

export default function Ground() {
  const [seats, setSeats] = useState({ premium: 0, standard: 0, economy: 0, standing: 0 });
  const [seatsDirty, setSeatsDirty] = useState(false);
  const [seatsSaving, setSeatsSaving] = useState(false);
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        const [seatsRes, matchesRes] = await Promise.all([
          getStadiumSeats(),
          getUpcomingHomeMatches(),
        ]);
        const s = seatsRes.data;
        setSeats({ premium: s.premium, standard: s.standard, economy: s.economy, standing: s.standing });
        setMatches(matchesRes.data);
      } catch {
        toast.error('Failed to load ground data');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const totalSeats = seats.premium + seats.standard + seats.economy + seats.standing;

  const adjustSeats = (type, delta) => {
    setSeats((prev) => ({ ...prev, [type]: Math.max(0, prev[type] + delta) }));
    setSeatsDirty(true);
  };

  const handleSaveSeats = async () => {
    setSeatsSaving(true);
    try {
      const res = await updateStadiumSeats(seats);
      const s = res.data;
      setSeats({ premium: s.premium, standard: s.standard, economy: s.economy, standing: s.standing });
      setSeatsDirty(false);
      toast.success('Seats updated');
    } catch {
      toast.error('Failed to update seats');
    } finally {
      setSeatsSaving(false);
    }
  };

  const handlePitchChange = async (matchId, pitchType) => {
    // Optimistic update
    setMatches((prev) =>
      prev.map((m) => (m.id === matchId ? { ...m, pitchType } : m))
    );
    try {
      await updateMatchPitch(matchId, pitchType);
      toast.success('Pitch updated');
    } catch {
      toast.error('Failed to update pitch');
      // Revert
      const res = await getUpcomingHomeMatches();
      setMatches(res.data);
    }
  };

  if (loading) {
    return (
      <div className="ground-page">
        <div className="ground-loading">Loading ground data…</div>
      </div>
    );
  }

  return (
    <div className="ground-page">
      {/* ── Header ── */}
      <div className="ground-header">
        <div className="ground-header-left">
          <HiOutlineBuildingOffice2 className="ground-header-icon" />
          <div>
            <h1>Ground Management</h1>
            <p>Configure your stadium seating and set the pitch for each upcoming home match.</p>
          </div>
        </div>
      </div>

      {/* ── Stadium Seating ── */}
      <div className="ground-card">
        <div className="ground-card-head">
          <h2>Stadium Seating</h2>
          <span className="ground-total-badge">
            Total: <strong>{totalSeats.toLocaleString()}</strong>
          </span>
        </div>

        <div className="seat-grid">
          {seatTypes.map((s) => (
            <div className="seat-card" key={s.key}>
              <div className="seat-card-top">
                <span className="seat-card-icon">{s.icon}</span>
                <span className="seat-card-label" style={{ color: s.color }}>{s.label}</span>
              </div>
              <p className="seat-card-desc">{s.desc}</p>
              <div className="seat-card-controls">
                <button
                  className="seat-btn"
                  onClick={() => adjustSeats(s.key, -500)}
                  disabled={seats[s.key] <= 0}
                  aria-label={`Decrease ${s.label} by 500`}
                >
                  <HiOutlineMinus />
                </button>
                <span className="seat-card-count" style={{ color: s.color }}>
                  {seats[s.key].toLocaleString()}
                </span>
                <button
                  className="seat-btn"
                  onClick={() => adjustSeats(s.key, 500)}
                  aria-label={`Increase ${s.label} by 500`}
                >
                  <HiOutlinePlus />
                </button>
              </div>
            </div>
          ))}
        </div>

        {seatsDirty && (
          <div className="ground-save-row">
            <button
              className="ground-save-btn"
              onClick={handleSaveSeats}
              disabled={seatsSaving}
            >
              {seatsSaving ? 'Saving…' : 'Save Seating'}
            </button>
          </div>
        )}
      </div>

      {/* ── Pitch Setup ── */}
      <div className="ground-card">
        <div className="ground-card-head">
          <h2>Pitch Setup — Upcoming Home Matches</h2>
          <span className="ground-total-badge">{matches.length} matches</span>
        </div>

        {matches.length === 0 ? (
          <p className="ground-empty">No upcoming home matches scheduled.</p>
        ) : (
          <div className="match-list">
            {matches.map((match) => {
              const badge = formatBadge(match.format);
              return (
                <div className="match-row" key={match.id}>
                  <div className="match-info">
                    <div className="match-info-top">
                      <span
                        className="match-format-tag"
                        style={{ background: badge.color + '18', color: badge.color, borderColor: badge.color + '40' }}
                      >
                        {badge.label}
                      </span>
                      <span className="match-comp">{match.competition} · Div {match.leagueLabel} · R{match.round}</span>
                    </div>
                    <span className="match-opponent">{match.opponentName}</span>
                    <span className="match-date">{format(parseISO(match.matchDate), 'EEE, dd MMM yyyy')}</span>
                  </div>

                  <div className="match-pitch-picker">
                    <label>Pitch</label>
                    <div className="pitch-select-wrap">
                      <select
                        value={match.pitchType}
                        onChange={(e) => handlePitchChange(match.id, e.target.value)}
                      >
                        {pitchOptions.map((p) => (
                          <option key={p.key} value={p.key}>
                            {p.icon} {p.label}
                          </option>
                        ))}
                      </select>
                    </div>
                    <span className="pitch-hint">
                      {pitchOptions.find((p) => p.key === match.pitchType)?.desc}
                    </span>
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
