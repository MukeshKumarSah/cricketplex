import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  HiOutlineUserGroup,
  HiOutlineFunnel,
  HiOutlineBarsArrowDown,
} from 'react-icons/hi2';
import { getSquad } from '../../api/auth';
import './Squad.css';

const ROLE_ORDER = { BATSMAN: 0, KEEPER: 1, ALL_ROUNDER: 2, BOWLER: 3 };
const ROLE_LABELS = { BATSMAN: 'Batsman', KEEPER: 'Keeper', ALL_ROUNDER: 'All-Rounder', BOWLER: 'Bowler' };
const ROLE_SHORT = { BATSMAN: 'BAT', KEEPER: 'WK', ALL_ROUNDER: 'AR', BOWLER: 'BOWL' };
const AGG_LABELS = { D: 'Defensive', N: 'Neutral', A: 'Aggressive' };
const BOWL_TYPE_LABELS = { FS: 'Finger Spin', WS: 'Wrist Spin', F: 'Fast', M: 'Medium', FM: 'Fast-Medium', MF: 'Medium-Fast' };

const SORT_OPTIONS = [
  { value: '', label: '— None —' },
  { value: 'batRating', label: 'Batting' },
  { value: 'bowlRating', label: 'Bowling' },
  { value: 'keeperRating', label: 'Keeping' },
  { value: 'fldRating', label: 'Fielding' },
  { value: 'experience', label: 'Experience' },
  { value: 'stamina', label: 'Stamina' },
  { value: 'confidence', label: 'Confidence' },
  { value: 'fitness', label: 'Fitness' },
  { value: 'age', label: 'Age' },
  { value: 'wage', label: 'Wage' },
  { value: 'country', label: 'Country' },
  { value: 'batHand', label: 'Bat Hand' },
  { value: 'bowlHand', label: 'Bowl Hand' },
  { value: 'bowlType', label: 'Bowl Type' },
];

export default function Squad() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [sort1, setSort1] = useState({ key: '', dir: 'desc' });
  const [sort2, setSort2] = useState({ key: '', dir: 'desc' });
  const [sort3, setSort3] = useState({ key: '', dir: 'desc' });

  useEffect(() => {
    (async () => {
      try {
        const res = await getSquad();
        setData(res.data);
      } catch {
        toast.error('Failed to load squad');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const players = useMemo(() => {
    if (!data?.players) return [];
    let list = [...data.players];
    if (roleFilter !== 'ALL') {
      list = list.filter((p) => p.role === roleFilter);
    }

    const sorts = [sort1, sort2, sort3].filter((s) => s.key);
    if (sorts.length === 0) {
      list.sort((a, b) => (ROLE_ORDER[a.role] ?? 9) - (ROLE_ORDER[b.role] ?? 9));
    } else {
      list.sort((a, b) => {
        for (const s of sorts) {
          const av = a[s.key], bv = b[s.key];
          let cmp = 0;
          if (typeof av === 'string' || typeof bv === 'string') {
            cmp = (av || '').localeCompare(bv || '');
          } else {
            cmp = (av || 0) - (bv || 0);
          }
          if (cmp !== 0) return s.dir === 'asc' ? cmp : -cmp;
        }
        return 0;
      });
    }
    return list;
  }, [data, roleFilter, sort1, sort2, sort3]);

  const roleCounts = useMemo(() => {
    if (!data?.players) return {};
    const counts = { ALL: data.players.length };
    for (const p of data.players) {
      counts[p.role] = (counts[p.role] || 0) + 1;
    }
    return counts;
  }, [data]);

  if (loading) {
    return <div className="sq-page"><div className="sq-loading">Loading squad…</div></div>;
  }

  if (!data || !data.players || data.players.length === 0) {
    return (
      <div className="sq-page">
        <div className="sq-empty">
          <HiOutlineUserGroup className="sq-empty-icon" />
          <h2>No Players Yet</h2>
          <p>Your squad is empty. Players are generated when you set up your team.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="sq-page">
      {/* Header */}
      <div className="sq-header">
        <div className="sq-header-left">
          <HiOutlineUserGroup className="sq-header-icon" />
          <div>
            <h1>{data.teamName}</h1>
            <p>{data.country} · {data.players.length} Players</p>
          </div>
        </div>
      </div>

      {/* Role Filter Tabs */}
      <div className="sq-filters">
        <HiOutlineFunnel className="sq-filter-icon" />
        {['ALL', 'BATSMAN', 'KEEPER', 'ALL_ROUNDER', 'BOWLER'].map((r) => (
          <button
            key={r}
            className={`sq-filter-btn ${roleFilter === r ? 'active' : ''}`}
            onClick={() => setRoleFilter(r)}
          >
            {r === 'ALL' ? 'All' : ROLE_LABELS[r]}
            <span className="sq-filter-count">{roleCounts[r] || 0}</span>
          </button>
        ))}
      </div>

      {/* Sort Controls */}
      <div className="sq-sort-bar">
        <HiOutlineBarsArrowDown className="sq-sort-icon" />
        {[
          { label: '1st', state: sort1, setter: setSort1 },
          { label: '2nd', state: sort2, setter: setSort2 },
          { label: '3rd', state: sort3, setter: setSort3 },
        ].map(({ label, state, setter }) => (
          <div key={label} className="sq-sort-group">
            <span className="sq-sort-label">{label}</span>
            <select
              className="sq-sort-select"
              value={state.key}
              onChange={(e) => setter({ ...state, key: e.target.value })}
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            {state.key && (
              <button
                className="sq-sort-dir"
                onClick={() => setter({ ...state, dir: state.dir === 'desc' ? 'asc' : 'desc' })}
                title={state.dir === 'desc' ? 'Descending' : 'Ascending'}
              >
                {state.dir === 'desc' ? '↓' : '↑'}
              </button>
            )}
          </div>
        ))}
        {(sort1.key || sort2.key || sort3.key) && (
          <button
            className="sq-sort-clear"
            onClick={() => {
              setSort1({ key: '', dir: 'desc' });
              setSort2({ key: '', dir: 'desc' });
              setSort3({ key: '', dir: 'desc' });
            }}
          >
            Clear
          </button>
        )}
      </div>

      {/* Player Cards */}
      <div className="sq-grid">
        {players.map((p) => (
          <div
            key={p.id}
            className="sq-card"
            onClick={() => navigate(`/player/${p.id}`)}
          >
            {/* Card Top */}
            <div className="sq-card-top">
              <div className="sq-card-name-row">
                <span className={`sq-role-badge ${p.role.toLowerCase()}`}>{ROLE_SHORT[p.role]}</span>
                <div className="sq-card-name">
                  <span className="sq-first">{p.firstName}</span>
                  <span className="sq-last">{p.lastName}</span>
                </div>
              </div>
              <div className="sq-card-meta">
                <span className="sq-meta-item">{p.nationality || p.country}</span>
                <span className="sq-meta-sep">·</span>
                <span className="sq-meta-item">Age {p.age}yr {p.ageDays ?? 0}d</span>
                <span className="sq-meta-sep">·</span>
                <span className="sq-meta-item">{p.batHand} Bat</span>
                {p.bowlHand && (
                  <>
                    <span className="sq-meta-sep">·</span>
                    <span className="sq-meta-item">{p.bowlHand} {BOWL_TYPE_LABELS[p.bowlType] || p.bowlType}</span>
                  </>
                )}
              </div>
              <div className="sq-card-meta">
                <span className="sq-meta-item">⭐ {p.rating}</span>
                <span className="sq-meta-sep">·</span>
                <span className="sq-meta-item">💰 ${p.wage}/wk</span>
                <span className="sq-meta-sep">·</span>
                <span className="sq-meta-item">{AGG_LABELS[p.batAggression]} Bat</span>
                <span className="sq-meta-sep">·</span>
                <span className="sq-meta-item">{AGG_LABELS[p.bowlAggression]} Bowl</span>
              </div>
            </div>

            {/* Rating Bars - two columns */}
            <div className="sq-ratings-split">
              <div className="sq-ratings-col">
                <RatingBar label="BAT" value={p.batRating} max={100} color="#22c55e" />
                <RatingBar label="BOWL" value={p.bowlRating} max={100} color="#3b82f6" />
                <RatingBar label="WK" value={p.keeperRating} max={100} color="#f59e0b" />
                <RatingBar label="FLD" value={p.fldRating} max={100} color="#8b5cf6" />
              </div>
              <div className="sq-ratings-divider" />
              <div className="sq-ratings-col">
                <RatingBar label="STA" value={p.stamina} max={100} color="#fb923c" />
                <RatingBar label="EXP" value={p.experience} max={100} color="#22d3ee" />
                <RatingBar label="CONF" value={p.confidence} max={100} color="#f472b6" />
                <RatingBar label="FIT" value={p.fitness} max={100} color="#a3e635" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function RatingBar({ label, value, max, color }) {
  const pct = Math.min((value / max) * 100, 100);
  return (
    <div className="sq-rating-row">
      <span className="sq-rating-label">{label}</span>
      <div className="sq-rating-track">
        <div className="sq-rating-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="sq-rating-value">{Math.floor(value)}</span>
    </div>
  );
}
