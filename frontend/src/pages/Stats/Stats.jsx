import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getTeamStats, getCurrentSeason } from '../../api/auth';
import { compareStats } from '../../utils/compareStats';
import toast from 'react-hot-toast';
import './Stats.css';

const FORMATS = ['T20', 'ODI', 'FC'];
const MATCH_TYPES = [
  { value: 'LEAGUE', label: 'Official' },
  { value: 'FRIENDLY', label: 'Friendly' },
  { value: 'CUP', label: 'Cup' },
];
const ROLE_SHORT = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };

export default function Stats() {
  const [format, setFormat] = useState(() => sessionStorage.getItem('stats_format') || 'T20');
  const [matchType, setMatchType] = useState(() => sessionStorage.getItem('stats_matchType') || 'LEAGUE');
  const [season, setSeason] = useState(() => {
    const saved = sessionStorage.getItem('stats_season');
    if (saved == null || saved === 'all') return null;
    const n = Number(saved);
    return Number.isFinite(n) ? n : null;
  });
  const [maxSeason, setMaxSeason] = useState(1);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState(() => sessionStorage.getItem('stats_tab') || 'batting');
  const [sortField, setSortField] = useState(null);
  const [sortOrder, setSortOrder] = useState('desc');

  useEffect(() => {
    getCurrentSeason()
      .then(r => {
        const s = r.data?.season;
        if (s) {
          setMaxSeason(s);
          setSeason(prev => (prev != null && prev > s) ? s : prev);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    getTeamStats(format, matchType, season)
      .then(res => setData(res.data))
      .catch(() => toast.error('Failed to load stats'))
      .finally(() => setLoading(false));
  }, [format, matchType, season]);

  useEffect(() => {
    sessionStorage.setItem('stats_format', format);
  }, [format]);
  useEffect(() => {
    sessionStorage.setItem('stats_matchType', matchType);
  }, [matchType]);
  useEffect(() => {
    sessionStorage.setItem('stats_tab', tab);
  }, [tab]);
  useEffect(() => {
    sessionStorage.setItem('stats_season', season == null ? 'all' : String(season));
  }, [season]);

  const handleSort = (field) => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('desc');
    }
  };

  const sortData = (rows, field, order) => {
    if (!field || !rows) return rows;
    const dir = order === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => dir * compareStats(a[field], b[field]));
  };

  if (loading) return <div className="st-loading">Loading stats…</div>;
  if (!data) return <div className="st-loading">No data available</div>;

  const tabs = [
    { key: 'batting', label: 'Batting' },
    { key: 'bowling', label: 'Bowling' },
    { key: 'fielding', label: 'Fielding' },
  ];

  return (
    <div className="st-page">
      <div className="st-header">
        <h1 className="st-title">Team Stats</h1>
        <div className="st-controls">
          <select
            className="st-format-select"
            value={format}
            onChange={e => setFormat(e.target.value)}
          >
            {FORMATS.map(f => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
          <div className="st-type-toggle">
            {MATCH_TYPES.map(mt => (
              <button
                key={mt.value}
                className={`st-type-btn ${matchType === mt.value ? 'active' : ''}`}
                onClick={() => setMatchType(mt.value)}
              >{mt.label}</button>
            ))}
          </div>
          <div className="st-season-select">
            <label htmlFor="st-season">Season:</label>
            <select
              id="st-season"
              value={season == null ? 'all' : season}
              onChange={e => setSeason(e.target.value === 'all' ? null : Number(e.target.value))}
            >
              <option value="all">All seasons</option>
              {Array.from({ length: maxSeason }, (_, i) => maxSeason - i).map(s => (
                <option key={s} value={s}>Season {s}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="st-tabs">
        {tabs.map(t => (
          <button
            key={t.key}
            className={`st-tab ${tab === t.key ? 'active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="st-table-wrap">
        {tab === 'batting' && <BattingTable rows={data.batting} sortField={sortField} sortOrder={sortOrder} onSort={handleSort} sortData={sortData} />}
        {tab === 'bowling' && <BowlingTable rows={data.bowling} sortField={sortField} sortOrder={sortOrder} onSort={handleSort} sortData={sortData} />}
        {tab === 'fielding' && <FieldingTable rows={data.fielding} sortField={sortField} sortOrder={sortOrder} onSort={handleSort} sortData={sortData} />}
      </div>
    </div>
  );
}

function BattingTable({ rows, sortField, sortOrder, onSort, sortData }) {
  if (!rows?.length) return <div className="st-empty">No batting data for this format</div>;
  const sorted = sortField ? sortData(rows, sortField, sortOrder) : rows;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name st-sortable" onClick={() => onSort('name')}>
            Player {sortField === 'name' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('matches')}>
            Mat {sortField === 'matches' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('innings')}>
            Inn {sortField === 'innings' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('notOuts')}>
            NO {sortField === 'notOuts' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-highlight-col st-sortable" onClick={() => onSort('runs')}>
            Runs {sortField === 'runs' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('highest')}>
            HS {sortField === 'highest' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('average')}>
            Avg {sortField === 'average' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('strikeRate')}>
            SR {sortField === 'strikeRate' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('hundreds')}>
            100s {sortField === 'hundreds' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('fifties')}>
            50s {sortField === 'fifties' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('fours')}>
            4s {sortField === 'fours' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('sixes')}>
            6s {sortField === 'sixes' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
        </tr>
      </thead>
      <tbody>
        {sorted.map(r => (
          <tr key={r.id} className="st-row">
            <td className="st-td-name">
              <Link to={`/player/${r.id}`} className="st-player-name">{r.name}</Link>
              <span className="st-player-role">{ROLE_SHORT[r.role] || r.role}</span>
            </td>
            <td>{r.matches}</td>
            <td>{r.innings}</td>
            <td>{r.notOuts}</td>
            <td className="st-highlight-col">{r.runs}</td>
            <td>{r.highest}</td>
            <td>{r.average}</td>
            <td>{r.strikeRate}</td>
            <td>{r.hundreds}</td>
            <td>{r.fifties}</td>
            <td>{r.fours}</td>
            <td>{r.sixes}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function BowlingTable({ rows, sortField, sortOrder, onSort, sortData }) {
  if (!rows?.length) return <div className="st-empty">No bowling data for this format</div>;
  const sorted = sortField ? sortData(rows, sortField, sortOrder) : rows;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name st-sortable" onClick={() => onSort('name')}>
            Player {sortField === 'name' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('matches')}>
            Mat {sortField === 'matches' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('innings')}>
            Inn {sortField === 'innings' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('overs')}>
            Overs {sortField === 'overs' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('runs')}>
            Runs {sortField === 'runs' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-highlight-col st-sortable" onClick={() => onSort('wickets')}>
            Wkts {sortField === 'wickets' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('best')}>
            Best {sortField === 'best' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('average')}>
            Avg {sortField === 'average' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('economy')}>
            Econ {sortField === 'economy' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('strikeRate')}>
            SR {sortField === 'strikeRate' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('maidens')}>
            Mdns {sortField === 'maidens' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('fiveWickets')}>
            5W {sortField === 'fiveWickets' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('threeWickets')}>
            3W {sortField === 'threeWickets' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
        </tr>
      </thead>
      <tbody>
        {sorted.map(r => (
          <tr key={r.id} className="st-row">
            <td className="st-td-name">
              <Link to={`/player/${r.id}`} className="st-player-name">{r.name}</Link>
              <span className="st-player-role">{ROLE_SHORT[r.role] || r.role}</span>
            </td>
            <td>{r.matches}</td>
            <td>{r.innings}</td>
            <td>{r.overs}</td>
            <td>{r.runs}</td>
            <td className="st-highlight-col">{r.wickets}</td>
            <td>{r.best}</td>
            <td>{r.average}</td>
            <td>{r.economy}</td>
            <td>{r.strikeRate}</td>
            <td>{r.maidens}</td>
            <td>{r.fiveWickets}</td>
            <td>{r.threeWickets}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function FieldingTable({ rows, sortField, sortOrder, onSort, sortData }) {
  if (!rows?.length) return <div className="st-empty">No fielding data for this format</div>;
  const sorted = sortField ? sortData(rows, sortField, sortOrder) : rows;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name st-sortable" onClick={() => onSort('name')}>
            Player {sortField === 'name' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('matches')}>
            Mat {sortField === 'matches' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-highlight-col st-sortable" onClick={() => onSort('catches')}>
            Ct {sortField === 'catches' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('stumpings')}>
            St {sortField === 'stumpings' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-sortable" onClick={() => onSort('runOuts')}>
            RO {sortField === 'runOuts' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
          <th className="st-highlight-col st-sortable" onClick={() => onSort('total')}>
            Total {sortField === 'total' && <span className="st-sort-icon">{sortOrder === 'asc' ? '↑' : '↓'}</span>}
          </th>
        </tr>
      </thead>
      <tbody>
        {sorted.map(r => (
          <tr key={r.id} className="st-row">
            <td className="st-td-name">
              <Link to={`/player/${r.id}`} className="st-player-name">{r.name}</Link>
              <span className="st-player-role">{ROLE_SHORT[r.role] || r.role}</span>
            </td>
            <td>{r.matches}</td>
            <td className="st-highlight-col">{r.catches}</td>
            <td>{r.stumpings}</td>
            <td>{r.runOuts}</td>
            <td className="st-highlight-col">{r.total}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
