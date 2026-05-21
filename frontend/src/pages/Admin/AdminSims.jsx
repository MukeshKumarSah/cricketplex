import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import {
  HiOutlineBeaker,
  HiOutlineTrash,
  HiOutlineEye,
  HiOutlinePlus,
  HiOutlineArrowLeft,
  HiOutlineCheckCircle,
  HiOutlineClock,
  HiOutlineExclamationCircle,
  HiOutlineArrowPath,
} from 'react-icons/hi2';
import {
  createSimSession,
  listSimSessions,
  getSimSession,
  deleteSimSession,
} from '../../api/auth';
import './AdminSims.css';

// ─── Defaults ────────────────────────────────────────────────────────────────

const ROLES = ['BATSMAN', 'BOWLER', 'ALL_ROUNDER', 'KEEPER'];
const HANDS = ['RH', 'LH'];
const BOWL_TYPES = ['FS', 'WS', 'F', 'M', 'FM', 'MF'];
const AGGRESSION = ['D', 'N', 'A'];
const PITCH_TYPES = ['STANDARD', 'BATTING', 'BOWLING', 'SPIN', 'FAST', 'BALANCED'];

function blankPlayer(pos) {
  return {
    name: `Player ${pos}`,
    role: pos === 7 ? 'KEEPER' : pos <= 5 ? 'BATSMAN' : 'BOWLER',
    battingPosition: pos,
    batRating: 60,
    bowlRating: pos <= 5 ? 20 : 65,
    keeperRating: pos === 7 ? 70 : 0,
    fldRating: 55,
    stamina: 70,
    confidence: 55,
    batHand: 'RH',
    bowlHand: pos <= 5 ? 'RH' : 'RH',
    bowlType: pos <= 5 ? null : 'F',
    batAggression: 'N',
    bowlAggression: 'N',
    bowlOvers: pos <= 5 ? 0 : 4,
  };
}

function defaultTeam() {
  return Array.from({ length: 11 }, (_, i) => blankPlayer(i + 1));
}

// ─── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ status }) {
  const map = {
    DONE: { cls: 'sim-badge-done', icon: HiOutlineCheckCircle, label: 'Done' },
    RUNNING: { cls: 'sim-badge-running', icon: HiOutlineArrowPath, label: 'Running' },
    PENDING: { cls: 'sim-badge-pending', icon: HiOutlineClock, label: 'Pending' },
    ERROR: { cls: 'sim-badge-error', icon: HiOutlineExclamationCircle, label: 'Error' },
  };
  const cfg = map[status] || map.PENDING;
  const Icon = cfg.icon;
  return (
    <span className={`sim-badge ${cfg.cls}`}>
      <Icon /> {cfg.label}
    </span>
  );
}

// ─── Results view ─────────────────────────────────────────────────────────────

function ResultsView({ session }) {
  const r = session.result;
  if (!r) return <p className="sim-no-result">No results available yet.</p>;

  const fmtArr = (arr) => (arr || []).map((v) => (v ?? 0).toFixed(1)).join(', ');

  return (
    <div className="sim-results">
      {/* Summary */}
      <div className="sim-result-section">
        <h3 className="sim-section-title">Match Summary</h3>
        <div className="sim-summary-grid">
          <div className="sim-summary-card"><span>{r.totalMatches}</span><label>Matches</label></div>
          <div className="sim-summary-card"><span>{r.teamAWins}</span><label>{r.teamAName} Wins</label></div>
          <div className="sim-summary-card"><span>{r.teamBWins}</span><label>{r.teamBName} Wins</label></div>
          <div className="sim-summary-card"><span>{r.draws}</span><label>Draws</label></div>
          <div className="sim-summary-card"><span>{r.battingFirstWins}</span><label>Bat-First Wins</label></div>
          <div className="sim-summary-card"><span>{r.battingSecondWins}</span><label>Chase Wins</label></div>
          <div className="sim-summary-card"><span>{r.avgFirstInningsRuns?.toFixed(1)}</span><label>Avg 1st Inn Runs</label></div>
          <div className="sim-summary-card"><span>{r.avgSecondInningsRuns?.toFixed(1)}</span><label>Avg 2nd Inn Runs</label></div>
        </div>
      </div>

      {/* Score Distribution */}
      {r.scoreDistribution && (
        <div className="sim-result-section">
          <h3 className="sim-section-title">Score Distribution (1st Innings)</h3>
          <table className="sim-table">
            <thead>
              <tr><th>Bucket</th><th>Matches</th><th>Chase Wins</th></tr>
            </thead>
            <tbody>
              {Object.entries(r.scoreDistribution).map(([key, val]) => (
                <tr key={key}>
                  <td>{key.replace('to', '–').replace('under', '<').replace('above', '350+')}</td>
                  <td>{val.count}</td>
                  <td>{val.chaseWins}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Progressive Scores */}
      {r.progressiveScores && (
        <div className="sim-result-section">
          <h3 className="sim-section-title">Progressive Scores (avg runs at over milestone)</h3>
          <table className="sim-table">
            <thead>
              <tr>
                <th>Team</th>
                {(r.progressiveScores.milestones || []).map((m) => (
                  <th key={m}>Over {m}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="sim-td-bold">{r.teamAName} (Inn1)</td>
                {(r.progressiveScores.teamA?.innings1 || []).map((v, i) => (
                  <td key={i}>{Number(v).toFixed(1)}</td>
                ))}
              </tr>
              <tr>
                <td className="sim-td-bold">{r.teamAName} (Inn2)</td>
                {(r.progressiveScores.teamA?.innings2 || []).map((v, i) => (
                  <td key={i}>{Number(v).toFixed(1)}</td>
                ))}
              </tr>
              <tr>
                <td className="sim-td-bold">{r.teamBName} (Inn1)</td>
                {(r.progressiveScores.teamB?.innings1 || []).map((v, i) => (
                  <td key={i}>{Number(v).toFixed(1)}</td>
                ))}
              </tr>
              <tr>
                <td className="sim-td-bold">{r.teamBName} (Inn2)</td>
                {(r.progressiveScores.teamB?.innings2 || []).map((v, i) => (
                  <td key={i}>{Number(v).toFixed(1)}</td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {/* Batting Stats */}
      {r.batting && (
        <div className="sim-result-section">
          <h3 className="sim-section-title">Batting Stats</h3>
          {[r.teamAName, r.teamBName].map((tn, ti) => {
            const rows = ti === 0 ? r.batting.teamA : r.batting.teamB;
            return (
              <div key={tn} className="sim-team-block">
                <h4 className="sim-team-sub">{tn}</h4>
                <table className="sim-table">
                  <thead>
                    <tr><th>Player</th><th>Inn</th><th>Runs</th><th>Avg</th><th>Balls</th><th>SR</th><th>4s</th><th>6s</th><th>50s</th><th>100s</th><th>HS</th></tr>
                  </thead>
                  <tbody>
                    {(rows || []).map((row) => (
                      <tr key={row.name}>
                        <td className="sim-td-bold">{row.name}</td>
                        <td>{row.innings}</td>
                        <td>{row.runs}</td>
                        <td>{Number(row.avg).toFixed(2)}</td>
                        <td>{row.balls}</td>
                        <td>{Number(row.sr).toFixed(2)}</td>
                        <td>{row.fours}</td>
                        <td>{row.sixes}</td>
                        <td>{row.fifties}</td>
                        <td>{row.hundreds}</td>
                        <td>{row.highest}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          })}
        </div>
      )}

      {/* Bowling Stats */}
      {r.bowling && (
        <div className="sim-result-section">
          <h3 className="sim-section-title">Bowling Stats</h3>
          {[r.teamAName, r.teamBName].map((tn, ti) => {
            const rows = ti === 0 ? r.bowling.teamA : r.bowling.teamB;
            return (
              <div key={tn} className="sim-team-block">
                <h4 className="sim-team-sub">{tn}</h4>
                <table className="sim-table">
                  <thead>
                    <tr><th>Player</th><th>Inn</th><th>Overs</th><th>M</th><th>Runs</th><th>Wkts</th><th>Avg</th><th>Econ</th><th>SR</th><th>3W</th><th>5W</th></tr>
                  </thead>
                  <tbody>
                    {(rows || []).map((row) => (
                      <tr key={row.name}>
                        <td className="sim-td-bold">{row.name}</td>
                        <td>{row.innings}</td>
                        <td>{row.overs}</td>
                        <td>{row.maidens}</td>
                        <td>{row.runs}</td>
                        <td>{row.wickets}</td>
                        <td>{Number(row.avg || 0).toFixed(2)}</td>
                        <td>{Number(row.econ || 0).toFixed(2)}</td>
                        <td>{Number(row.sr || 0).toFixed(1)}</td>
                        <td>{row.threeWickets}</td>
                        <td>{row.fiveWickets}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          })}
        </div>
      )}

      {/* Fielding Stats */}
      {r.fielding && (
        <div className="sim-result-section">
          <h3 className="sim-section-title">Fielding Stats</h3>
          {[r.teamAName, r.teamBName].map((tn, ti) => {
            const rows = ti === 0 ? r.fielding.teamA : r.fielding.teamB;
            return (
              <div key={tn} className="sim-team-block">
                <h4 className="sim-team-sub">{tn}</h4>
                <table className="sim-table">
                  <thead>
                    <tr><th>Player</th><th>Catches</th><th>Stumpings</th><th>Run-Outs</th></tr>
                  </thead>
                  <tbody>
                    {(rows || []).map((row) => (
                      <tr key={row.name}>
                        <td className="sim-td-bold">{row.name}</td>
                        <td>{row.catches}</td>
                        <td>{row.stumpings}</td>
                        <td>{row.runOuts}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ─── Player row editor ───────────────────────────────────────────────────────

function PlayerRow({ p, idx, onChange }) {
  const set = (field, val) => onChange(idx, field, val);
  return (
    <tr>
      <td>{p.battingPosition}</td>
      <td><input className="sim-input sim-input-name" value={p.name} onChange={(e) => set('name', e.target.value)} /></td>
      <td>
        <select className="sim-select" value={p.role} onChange={(e) => set('role', e.target.value)}>
          {ROLES.map((r) => <option key={r}>{r}</option>)}
        </select>
      </td>
      <td><input className="sim-input sim-input-num" type="number" min={1} max={100} value={Math.floor(p.batRating)} onChange={(e) => set('batRating', +e.target.value)} /></td>
      <td><input className="sim-input sim-input-num" type="number" min={1} max={100} value={Math.floor(p.bowlRating)} onChange={(e) => set('bowlRating', +e.target.value)} /></td>
      <td><input className="sim-input sim-input-num" type="number" min={0} max={100} value={Math.floor(p.keeperRating)} onChange={(e) => set('keeperRating', +e.target.value)} /></td>
      <td><input className="sim-input sim-input-num" type="number" min={1} max={100} value={Math.floor(p.fldRating)} onChange={(e) => set('fldRating', +e.target.value)} /></td>
      <td><input className="sim-input sim-input-num" type="number" min={1} max={100} value={Math.floor(p.stamina)} onChange={(e) => set('stamina', +e.target.value)} /></td>
      <td><input className="sim-input sim-input-num" type="number" min={1} max={100} value={Math.floor(p.confidence)} onChange={(e) => set('confidence', +e.target.value)} /></td>
      <td>
        <select className="sim-select" value={p.batHand} onChange={(e) => set('batHand', e.target.value)}>
          {HANDS.map((h) => <option key={h}>{h}</option>)}
        </select>
      </td>
      <td>
        <select className="sim-select" value={p.bowlHand || ''} onChange={(e) => set('bowlHand', e.target.value || null)}>
          <option value="">-</option>
          {HANDS.map((h) => <option key={h}>{h}</option>)}
        </select>
      </td>
      <td>
        <select className="sim-select" value={p.bowlType || ''} onChange={(e) => set('bowlType', e.target.value || null)}>
          <option value="">-</option>
          {BOWL_TYPES.map((t) => <option key={t}>{t}</option>)}
        </select>
      </td>
      <td>
        <select className="sim-select" value={p.batAggression} onChange={(e) => set('batAggression', e.target.value)}>
          {AGGRESSION.map((a) => <option key={a}>{a}</option>)}
        </select>
      </td>
      <td>
        <select className="sim-select" value={p.bowlAggression} onChange={(e) => set('bowlAggression', e.target.value)}>
          {AGGRESSION.map((a) => <option key={a}>{a}</option>)}
        </select>
      </td>
      <td><input className="sim-input sim-input-num" type="number" min={0} max={10} value={p.bowlOvers} onChange={(e) => set('bowlOvers', +e.target.value)} /></td>
    </tr>
  );
}

// ─── Team editor ──────────────────────────────────────────────────────────────

function TeamEditor({ label, players, setPlayers }) {
  const update = (idx, field, val) => {
    setPlayers((prev) => prev.map((p, i) => (i === idx ? { ...p, [field]: val } : p)));
  };
  const total = players.reduce((s, p) => s + (p.bowlOvers || 0), 0);

  return (
    <div className="sim-team-editor">
      <div className="sim-team-editor-header">
        <span className="sim-team-editor-label">{label}</span>
        <span className={`sim-overs-total ${total !== 0 ? (total !== 20 && total !== 50 ? 'sim-overs-warn' : 'sim-overs-ok') : ''}`}>
          Bowl Overs Total: {total}
        </span>
      </div>
      <div className="sim-table-scroll">
        <table className="sim-table sim-player-table">
          <thead>
            <tr>
              <th>#</th><th>Name</th><th>Role</th>
              <th>Bat</th><th>Bowl</th><th>WK</th><th>Fld</th>
              <th>Stm</th><th>Conf</th>
              <th>BatHd</th><th>BwlHd</th><th>Type</th>
              <th>BatAgg</th><th>BwlAgg</th><th>Overs</th>
            </tr>
          </thead>
          <tbody>
            {players.map((p, i) => (
              <PlayerRow key={i} p={p} idx={i} onChange={update} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ─── Main component ────────────────────────────────────────────────────────────

export default function AdminSims() {
  const [tab, setTab] = useState('sessions'); // 'sessions' | 'new' | 'results'
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewSession, setViewSession] = useState(null);
  const [viewLoading, setViewLoading] = useState(false);

  // New sim form state
  const [simName, setSimName] = useState('');
  const [format, setFormat] = useState('T20');
  const [numMatches, setNumMatches] = useState(10);
  const [pitchType, setPitchType] = useState('STANDARD');
  const [country, setCountry] = useState('England');
  const [teamAName, setTeamAName] = useState('Team Alpha');
  const [teamBName, setTeamBName] = useState('Team Beta');
  const [playersA, setPlayersA] = useState(defaultTeam);
  const [playersB, setPlayersB] = useState(defaultTeam);
  const [submitting, setSubmitting] = useState(false);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listSimSessions();
      setSessions(res.data);
    } catch {
      toast.error('Failed to load sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadSessions(); }, [loadSessions]);

  // Auto-refresh running/pending sessions
  useEffect(() => {
    const hasActive = sessions.some((s) => s.status === 'RUNNING' || s.status === 'PENDING');
    if (!hasActive) return;
    const id = setInterval(loadSessions, 5000);
    return () => clearInterval(id);
  }, [sessions, loadSessions]);

  const handleView = async (session) => {
    if (session.status !== 'DONE') {
      toast.error('Results only available for completed sessions');
      return;
    }
    setViewLoading(true);
    try {
      const res = await getSimSession(session.id);
      setViewSession(res.data);
      setTab('results');
    } catch {
      toast.error('Failed to load session results');
    } finally {
      setViewLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this simulation session? This cannot be undone.')) return;
    try {
      await deleteSimSession(id);
      toast.success('Session deleted');
      setSessions((prev) => prev.filter((s) => s.id !== id));
      if (viewSession?.id === id) { setViewSession(null); setTab('sessions'); }
    } catch {
      toast.error('Failed to delete session');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const totalA = playersA.reduce((s, p) => s + (p.bowlOvers || 0), 0);
    const totalB = playersB.reduce((s, p) => s + (p.bowlOvers || 0), 0);
    const expected = format === 'T20' ? 20 : 50;

    const nonZeroA = totalA !== 0;
    const nonZeroB = totalB !== 0;
    if (nonZeroA && totalA !== expected) {
      toast.error(`Team A bowl overs total is ${totalA}, expected ${expected} (or 0 for auto)`);
      return;
    }
    if (nonZeroB && totalB !== expected) {
      toast.error(`Team B bowl overs total is ${totalB}, expected ${expected} (or 0 for auto)`);
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        name: simName || `Sim ${new Date().toLocaleString()}`,
        format,
        numMatches,
        config: {
          teamAName,
          teamBName,
          pitchType,
          country,
          playersA,
          playersB,
        },
      };
      await createSimSession(payload);
      toast.success('Simulation started!');
      setTab('sessions');
      loadSessions();
    } catch {
      toast.error('Failed to create simulation');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sim-page">
      <div className="sim-header">
        <HiOutlineBeaker className="sim-header-icon" />
        <h1 className="sim-title">Simulation Lab</h1>
      </div>

      <div className="sim-tabs">
        <button className={`sim-tab ${tab === 'sessions' ? 'sim-tab-active' : ''}`} onClick={() => setTab('sessions')}>Sessions</button>
        <button className={`sim-tab ${tab === 'new' ? 'sim-tab-active' : ''}`} onClick={() => setTab('new')}>
          <HiOutlinePlus style={{ marginRight: 4 }} /> New Simulation
        </button>
        {viewSession && (
          <button className={`sim-tab ${tab === 'results' ? 'sim-tab-active' : ''}`} onClick={() => setTab('results')}>
            Results: {viewSession.name}
          </button>
        )}
      </div>

      {/* ── Sessions tab ── */}
      {tab === 'sessions' && (
        <div className="sim-sessions">
          <div className="sim-sessions-header">
            <span className="sim-sessions-count">{sessions.length} session(s)</span>
            <button className="sim-btn sim-btn-sm" onClick={loadSessions}><HiOutlineArrowPath /> Refresh</button>
          </div>
          {loading ? (
            <p className="sim-loading">Loading…</p>
          ) : sessions.length === 0 ? (
            <p className="sim-empty">No simulations yet. Create one!</p>
          ) : (
            <table className="sim-table">
              <thead>
                <tr><th>Name</th><th>Format</th><th>Matches</th><th>Status</th><th>Created</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {sessions.map((s) => (
                  <tr key={s.id}>
                    <td className="sim-td-bold">{s.name}</td>
                    <td>{s.format}</td>
                    <td>{s.numMatches}</td>
                    <td><StatusBadge status={s.status} /></td>
                    <td>{new Date(s.createdAt).toLocaleString()}</td>
                    <td className="sim-actions">
                      <button
                        className="sim-btn sim-btn-sm sim-btn-view"
                        onClick={() => handleView(s)}
                        disabled={viewLoading || s.status !== 'DONE'}
                        title={s.status !== 'DONE' ? 'Only available when Done' : 'View results'}
                      >
                        {viewLoading ? <HiOutlineArrowPath className="sim-spin" /> : <HiOutlineEye />} View
                      </button>
                      <button className="sim-btn sim-btn-sm sim-btn-delete" onClick={() => handleDelete(s.id)} title="Delete session">
                        <HiOutlineTrash /> Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* ── New simulation tab ── */}
      {tab === 'new' && (
        <form className="sim-form" onSubmit={handleSubmit}>
          <div className="sim-form-global">
            <div className="sim-form-field">
              <label>Session Name</label>
              <input className="sim-input" placeholder="My Simulation" value={simName} onChange={(e) => setSimName(e.target.value)} />
            </div>
            <div className="sim-form-field">
              <label>Format</label>
              <select className="sim-select" value={format} onChange={(e) => setFormat(e.target.value)}>
                <option value="T20">T20</option>
                <option value="ODI">ODI</option>
              </select>
            </div>
            <div className="sim-form-field">
              <label>Number of Matches</label>
              <input className="sim-input sim-input-num" type="number" min={1} max={1000} value={numMatches} onChange={(e) => setNumMatches(+e.target.value)} />
            </div>
            <div className="sim-form-field">
              <label>Pitch Type</label>
              <select className="sim-select" value={pitchType} onChange={(e) => setPitchType(e.target.value)}>
                {PITCH_TYPES.map((p) => <option key={p}>{p}</option>)}
              </select>
            </div>
            <div className="sim-form-field">
              <label>Country (for venue)</label>
              <input className="sim-input" value={country} onChange={(e) => setCountry(e.target.value)} />
            </div>
          </div>

          <div className="sim-teams-row">
            <div className="sim-team-name-field">
              <label>Team A Name</label>
              <input className="sim-input" value={teamAName} onChange={(e) => setTeamAName(e.target.value)} />
            </div>
            <div className="sim-team-name-field">
              <label>Team B Name</label>
              <input className="sim-input" value={teamBName} onChange={(e) => setTeamBName(e.target.value)} />
            </div>
          </div>

          <TeamEditor label={`Team A — ${teamAName}`} players={playersA} setPlayers={setPlayersA} />
          <TeamEditor label={`Team B — ${teamBName}`} players={playersB} setPlayers={setPlayersB} />

          <p className="sim-hint">
            Set "Bowl Overs" per player to define the bowling plan (total must equal {format === 'T20' ? 20 : 50} overs, or 0 for auto).
            Bowl-type and hand can be left blank for non-bowlers.
          </p>

          <button type="submit" className="sim-btn sim-btn-primary" disabled={submitting}>
            {submitting ? <><HiOutlineArrowPath className="sim-spin" /> Running…</> : <><HiOutlineBeaker /> Run Simulation</>}
          </button>
        </form>
      )}

      {/* ── Results tab ── */}
      {tab === 'results' && viewSession && (
        <div className="sim-results-tab">
          <button className="sim-btn sim-btn-sm" onClick={() => setTab('sessions')} style={{ marginBottom: '1rem' }}>
            <HiOutlineArrowLeft /> Back to Sessions
          </button>
          <div className="sim-result-meta">
            <strong>{viewSession.name}</strong> — {viewSession.format} · {viewSession.numMatches} matches
            <StatusBadge status={viewSession.status} />
          </div>
          {viewSession.status === 'ERROR' && (
            <div className="sim-error-box">{viewSession.errorMessage || 'Unknown error occurred.'}</div>
          )}
          <ResultsView session={viewSession} />
        </div>
      )}
    </div>
  );
}
