import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getTeamStats } from '../../api/auth';
import toast from 'react-hot-toast';
import './Stats.css';

const FORMATS = ['T20', 'ODI', 'FC'];
const MATCH_TYPES = [
  { value: 'LEAGUE', label: 'Official' },
  { value: 'FRIENDLY', label: 'Friendly' },
];
const ROLE_SHORT = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };

export default function Stats() {
  const navigate = useNavigate();
  const [format, setFormat] = useState(() => sessionStorage.getItem('stats_format') || 'T20');
  const [matchType, setMatchType] = useState(() => sessionStorage.getItem('stats_matchType') || 'LEAGUE');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState(() => sessionStorage.getItem('stats_tab') || 'batting');

  useEffect(() => {
    setLoading(true);
    getTeamStats(format, matchType)
      .then(res => setData(res.data))
      .catch(() => toast.error('Failed to load stats'))
      .finally(() => setLoading(false));
  }, [format, matchType]);

  useEffect(() => {
    sessionStorage.setItem('stats_format', format);
  }, [format]);
  useEffect(() => {
    sessionStorage.setItem('stats_matchType', matchType);
  }, [matchType]);
  useEffect(() => {
    sessionStorage.setItem('stats_tab', tab);
  }, [tab]);

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
        {tab === 'batting' && <BattingTable rows={data.batting} navigate={navigate} />}
        {tab === 'bowling' && <BowlingTable rows={data.bowling} navigate={navigate} />}
        {tab === 'fielding' && <FieldingTable rows={data.fielding} navigate={navigate} />}
      </div>
    </div>
  );
}

function BattingTable({ rows, navigate }) {
  if (!rows?.length) return <div className="st-empty">No batting data for this format</div>;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name">Player</th>
          <th>Mat</th>
          <th>Inn</th>
          <th>NO</th>
          <th className="st-highlight-col">Runs</th>
          <th>HS</th>
          <th>Avg</th>
          <th>SR</th>
          <th>100s</th>
          <th>50s</th>
          <th>4s</th>
          <th>6s</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} className="st-row" onClick={() => navigate(`/player/${r.id}`)}>
            <td className="st-td-name">
              <span className="st-player-name">{r.name}</span>
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

function BowlingTable({ rows, navigate }) {
  if (!rows?.length) return <div className="st-empty">No bowling data for this format</div>;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name">Player</th>
          <th>Mat</th>
          <th>Inn</th>
          <th>Overs</th>
          <th>Runs</th>
          <th className="st-highlight-col">Wkts</th>
          <th>Best</th>
          <th>Avg</th>
          <th>Econ</th>
          <th>SR</th>
          <th>Mdns</th>
          <th>5W</th>
          <th>3W</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} className="st-row" onClick={() => navigate(`/player/${r.id}`)}>
            <td className="st-td-name">
              <span className="st-player-name">{r.name}</span>
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

function FieldingTable({ rows, navigate }) {
  if (!rows?.length) return <div className="st-empty">No fielding data for this format</div>;
  return (
    <table className="st-table">
      <thead>
        <tr>
          <th className="st-th-name">Player</th>
          <th>Mat</th>
          <th className="st-highlight-col">Ct</th>
          <th>St</th>
          <th>RO</th>
          <th className="st-highlight-col">Total</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} className="st-row" onClick={() => navigate(`/player/${r.id}`)}>
            <td className="st-td-name">
              <span className="st-player-name">{r.name}</span>
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
