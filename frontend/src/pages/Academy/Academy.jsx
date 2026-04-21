import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getAcademyOverview, pullPlayer, getPullHistory, getPullStatus,
  assignTraining, removeTraining, getTrainingHistory,
  upgradeAcademy, downgradeAcademy,
} from '../../api/auth';
import toast from 'react-hot-toast';
import './Academy.css';

const ROLE_OPTIONS = [
  { value: 'BATSMAN', label: 'Batsman' },
  { value: 'BOWLER', label: 'Bowler' },
  { value: 'ALL_ROUNDER', label: 'All-Rounder' },
  { value: 'KEEPER', label: 'Keeper' },
];
const ROLE_SHORT = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };
const TRAINING_TYPES = [
  { value: 'BAT', label: 'Batting', desc: 'Bat + Stamina + Confidence' },
  { value: 'BOWL', label: 'Bowling', desc: 'Bowl + Confidence + Stamina' },
  { value: 'AR', label: 'All-Round', desc: 'Bat + Bowl + Confidence + Stamina' },
  { value: 'FLD', label: 'Fielding', desc: 'Fld + Stamina + Confidence' },
  { value: 'WK', label: 'Wicket-Keeping', desc: 'WK + Stamina + Confidence' },
  { value: 'STAMINA', label: 'Stamina', desc: 'Stamina + Confidence' },
  { value: 'MENTAL', label: 'Mental', desc: 'Confidence' },
];
const SKILL_LABELS = {
  batRating: 'Bat', bowlRating: 'Bowl', keeperRating: 'WK',
  fldRating: 'Fld', stamina: 'Stam', confidence: 'Conf',
};
const UPGRADE_COST = { 2: 40000, 3: 100000, 4: 200000 };
const DOWNGRADE_REFUND = { 4: 150000, 3: 75000, 2: 30000 };
const LEVEL_SPOTS = { 1: 3, 2: 5, 3: 7, 4: 10 };

export default function Academy() {
  const navigate = useNavigate();
  const [tab, setTab] = useState(() => sessionStorage.getItem('academy_tab') || 'pulls');
    useEffect(() => {
      sessionStorage.setItem('academy_tab', tab);
    }, [tab]);
  const [overview, setOverview] = useState(null);
  const [pullHistory, setPullHistory] = useState(null);
  const [trainingHistory, setTrainingHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [pullRole, setPullRole] = useState('BATSMAN');
  const [pulling, setPulling] = useState(false);
  const [lastPull, setLastPull] = useState(null);
  const [assigningId, setAssigningId] = useState(null);
  const [upgrading, setUpgrading] = useState(false);
  const [historySubTab, setHistorySubTab] = useState('pull-history');
  const [confirmModal, setConfirmModal] = useState(null);
  const [canPull, setCanPull] = useState(true);
  const [nextWindow, setNextWindow] = useState(null);

  const loadOverview = () => {
    getAcademyOverview()
      .then(res => setOverview(res.data))
      .catch(() => toast.error('Failed to load academy'))
      .finally(() => setLoading(false));
  };

  const loadPullStatus = () => {
    getPullStatus()
      .then(res => {
        setCanPull(res.data.canPull);
        setNextWindow(res.data.nextWindow);
      })
      .catch(() => {});
  };

  useEffect(() => { loadOverview(); loadPullStatus(); }, []);

  const handlePull = async () => {
    setPulling(true);
    try {
      const res = await pullPlayer(pullRole);
      setLastPull(res.data);
      setCanPull(false);
      loadPullStatus();
      toast.success(`Pulled ${res.data.player.name} from ${res.data.pulledFrom}!`);
      loadOverview();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Pull failed');
    } finally {
      setPulling(false);
    }
  };

  const handleAssign = async (playerId, type) => {
    try {
      await assignTraining(playerId, type);
      toast.success('Training assigned');
      setAssigningId(null);
      loadOverview();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to assign');
    }
  };

  const handleRemove = async (playerId) => {
    try {
      await removeTraining(playerId);
      toast.success('Training removed');
      loadOverview();
    } catch (e) {
      toast.error('Failed to remove');
    }
  };

  const loadPullHistory = () => {
    getPullHistory()
      .then(res => setPullHistory(res.data))
      .catch(() => toast.error('Failed to load pull history'));
  };

  const loadTrainingHistory = () => {
    getTrainingHistory()
      .then(res => setTrainingHistory(res.data))
      .catch(() => toast.error('Failed to load training history'));
  };

  useEffect(() => {
    if (tab === 'history' && historySubTab === 'pull-history' && !pullHistory) loadPullHistory();
    if (tab === 'history' && historySubTab === 'training-history' && !trainingHistory) loadTrainingHistory();
  }, [tab, historySubTab]);

  const fmt = (n) => '$' + Number(n).toLocaleString();

  const handleUpgrade = () => {
    const nextLevel = overview.academyLevel + 1;
    const cost = UPGRADE_COST[nextLevel];
    setConfirmModal({
      type: 'upgrade',
      title: `Upgrade to Level ${nextLevel}`,
      rows: [
        ['Cost', fmt(cost)],
        ['Current Funds', fmt(overview.funds)],
        ['After', fmt(overview.funds - cost)],
        ['Focused Slots', `${LEVEL_SPOTS[overview.academyLevel]} → ${LEVEL_SPOTS[nextLevel]}`],
      ],
    });
  };

  const handleDowngrade = () => {
    const curLevel = overview.academyLevel;
    const refund = DOWNGRADE_REFUND[curLevel];
    setConfirmModal({
      type: 'downgrade',
      title: `Downgrade to Level ${curLevel - 1}`,
      rows: [
        ['Refund', '+' + fmt(refund)],
        ['Current Funds', fmt(overview.funds)],
        ['After', fmt(overview.funds + refund)],
        ['Focused Slots', `${LEVEL_SPOTS[curLevel]} → ${LEVEL_SPOTS[curLevel - 1]}`],
      ],
      warning: 'Excess training assignments will be removed.',
    });
  };

  const confirmAction = async () => {
    const action = confirmModal.type;
    setConfirmModal(null);
    setUpgrading(true);
    try {
      const res = action === 'upgrade' ? await upgradeAcademy() : await downgradeAcademy();
      toast.success(res.data.message);
      loadOverview();
    } catch (e) {
      toast.error(e.response?.data?.error || `${action === 'upgrade' ? 'Upgrade' : 'Downgrade'} failed`);
    } finally {
      setUpgrading(false);
    }
  };

  if (loading) return <div className="ac-loading">Loading academy…</div>;
  if (!overview) return <div className="ac-loading">No data</div>;

  const tabs = [
    { key: 'pulls', label: 'Pulls' },
    { key: 'training', label: 'Training' },
    { key: 'history', label: 'History' },
  ];

  return (
    <div className="ac-page">
      {/* Header */}
      <div className="ac-header">
        <h1 className="ac-title">Academy</h1>
        <p className="ac-subtitle">Develop young talent and grow your facilities</p>
      </div>

      {/* Management Card */}
      <div className="ac-management-card">
        <div className="ac-mgmt-info">
          <div className="ac-mgmt-level-block">
            <span className="ac-mgmt-level-num">{overview.academyLevel}</span>
            <span className="ac-mgmt-level-label">Level Academy</span>
          </div>
          <div className="ac-mgmt-divider" />
          <div className="ac-mgmt-stat">
            <span className="ac-mgmt-stat-val">{overview.usedFocusedSpots} / {overview.maxFocusedSpots}</span>
            <span className="ac-mgmt-stat-label">Focused Spots</span>
          </div>
          <div className="ac-mgmt-divider" />
          <div className="ac-mgmt-stat">
            <span className="ac-mgmt-stat-val">{fmt(overview.funds)}</span>
            <span className="ac-mgmt-stat-label">Funds</span>
          </div>
        </div>
        <div className="ac-mgmt-actions">
          {overview.academyLevel < 4 && (
            <button className="ac-upgrade-btn" onClick={handleUpgrade} disabled={upgrading}>
              ▲ Upgrade to Level {overview.academyLevel + 1}
              <span className="ac-btn-cost">{fmt(UPGRADE_COST[overview.academyLevel + 1])}</span>
            </button>
          )}
          {overview.academyLevel > 1 && (
            <button className="ac-downgrade-btn" onClick={handleDowngrade} disabled={upgrading}>
              ▼ Downgrade to Level {overview.academyLevel - 1}
              <span className="ac-btn-refund">+{fmt(DOWNGRADE_REFUND[overview.academyLevel])} refund</span>
            </button>
          )}
          {overview.academyLevel === 4 && (
            <span className="ac-max-badge">⭐ Max Level</span>
          )}
        </div>
      </div>

      {/* Main Tabs */}
      <div className="ac-tabs">
        {tabs.map(t => (
          <button key={t.key} className={`ac-tab ${tab === t.key ? 'active' : ''}`}
            onClick={() => setTab(t.key)}>{t.label}</button>
        ))}
      </div>

      {tab === 'pulls' && (
        <PullsSection
          pullRole={pullRole} setPullRole={setPullRole}
          pulling={pulling} handlePull={handlePull} lastPull={lastPull}
          canPull={canPull} nextWindow={nextWindow}
          navigate={navigate}
        />
      )}

      {tab === 'training' && (
        <TrainingSection
          overview={overview}
          assigningId={assigningId} setAssigningId={setAssigningId}
          handleAssign={handleAssign} handleRemove={handleRemove}
          navigate={navigate}
        />
      )}

      {tab === 'history' && (
        <div className="ac-section">
          <div className="ac-history-subtabs">
            <button
              className={`ac-history-subtab subtab-pull ${historySubTab === 'pull-history' ? 'active' : ''}`}
              onClick={() => setHistorySubTab('pull-history')}
            >
              Pull History
            </button>
            <button
              className={`ac-history-subtab subtab-training ${historySubTab === 'training-history' ? 'active' : ''}`}
              onClick={() => setHistorySubTab('training-history')}
            >
              Training Logs
            </button>
          </div>
          {historySubTab === 'pull-history' && <PullHistorySection data={pullHistory} navigate={navigate} />}
          {historySubTab === 'training-history' && <TrainingHistorySection data={trainingHistory} navigate={navigate} />}
        </div>
      )}

      {confirmModal && (
        <div className="ac-modal-overlay" onClick={() => setConfirmModal(null)}>
          <div className="ac-modal" onClick={e => e.stopPropagation()}>
            <h3 className="ac-modal-title">{confirmModal.title}</h3>
            <div className="ac-modal-rows">
              {confirmModal.rows.map(([label, value], i) => (
                <div className="ac-modal-row" key={i}>
                  <span className="ac-modal-label">{label}</span>
                  <span className="ac-modal-value">{value}</span>
                </div>
              ))}
            </div>
            {confirmModal.warning && (
              <p className="ac-modal-warning">{confirmModal.warning}</p>
            )}
            <div className="ac-modal-actions">
              <button className="ac-modal-btn cancel" onClick={() => setConfirmModal(null)}>Cancel</button>
              <button
                className={`ac-modal-btn ${confirmModal.type === 'upgrade' ? 'confirm-upgrade' : 'confirm-downgrade'}`}
                onClick={confirmAction}
              >
                {confirmModal.type === 'upgrade' ? 'Upgrade' : 'Downgrade'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ════════════ Pulls Tab ════════════ */
function PullsSection({ pullRole, setPullRole, pulling, handlePull, lastPull, canPull, nextWindow, navigate }) {
  const formatNextWindow = () => {
    if (!nextWindow) return '';
    const d = new Date(nextWindow + 'Z');
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' }) + ' 1:00 AM UTC';
  };

  return (
    <div className="ac-section">
      <div className="ac-pull-card">
        <h3 className="ac-section-title">Player Pull</h3>
        {!canPull ? (
          <div className="ac-pull-cooldown">
            Weekly pull used. Next pull available: <strong>{formatNextWindow()}</strong>
          </div>
        ) : (
          <>
            <p className="ac-pull-desc">
              Pull a 17-year-old youth player. 15% chance from your home nation, 5% from each other country.
              <br />One pull per week. Resets every Sunday at 1:00 AM UTC.
            </p>
            <div className="ac-pull-controls">
              <select className="ac-select" value={pullRole} onChange={e => setPullRole(e.target.value)}>
                {ROLE_OPTIONS.map(r => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
              <button className="ac-pull-btn" onClick={handlePull} disabled={pulling}>
                {pulling ? 'Pulling…' : 'Pull Player'}
              </button>
            </div>
          </>
        )}
      </div>

      {lastPull && (
        <div className="ac-pull-result">
          <h3 className="ac-section-title">Last Pull</h3>
          <div className="ac-result-card" onClick={() => navigate(`/player/${lastPull.player.id}`)}>
            <div className="ac-result-top">
              <span className="ac-result-name">{lastPull.player.name}</span>
              <span className="ac-result-role">{ROLE_SHORT[lastPull.player.role]}</span>
              <span className="ac-result-country">{lastPull.pulledFrom}</span>
            </div>
            <div className="ac-result-stats">
              <StatPill label="Bat" value={lastPull.player.batRating} />
              <StatPill label="Bowl" value={lastPull.player.bowlRating} />
              {lastPull.player.keeperRating > 0 && <StatPill label="WK" value={lastPull.player.keeperRating} />}
              <StatPill label="Fld" value={lastPull.player.fldRating} />
              <StatPill label="Stam" value={lastPull.player.stamina} />
              <StatPill label="Conf" value={lastPull.player.confidence} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ════════════ Training Tab ════════════ */
function TrainingSection({ overview, assigningId, setAssigningId, handleAssign, handleRemove, navigate }) {
  const focused = overview.players.filter(p => p.trainingType);
  const general = overview.players.filter(p => !p.trainingType);

  return (
    <div className="ac-section">
      <div className="ac-training-info">
        <span>Focused Spots: <strong>{overview.usedFocusedSpots}</strong> / {overview.maxFocusedSpots}</span>
        <span className="ac-training-hint">Level {overview.academyLevel} Academy</span>
      </div>

      {focused.length > 0 && (
        <>
          <h3 className="ac-section-title">Focused Training</h3>
          <div className="ac-player-list">
            {focused.map(p => (
              <div key={p.id} className="ac-player-row">
                <div className="ac-player-info" onClick={() => navigate(`/player/${p.id}`)}>
                  <span className="ac-player-name">{p.name}</span>
                  <span className="ac-player-role-badge">{ROLE_SHORT[p.role]}</span>
                  <span className="ac-player-age">Age {p.age}yr {p.ageDays ?? 0}d</span>
                </div>
                <div className="ac-player-training">
                  <span className="ac-training-badge focused">{p.trainingType}</span>
                  {assigningId === p.id ? (
                    <div className="ac-type-picker">
                      {TRAINING_TYPES.map(t => (
                        <button key={t.value} className="ac-type-opt"
                          onClick={() => handleAssign(p.id, t.value)} title={t.desc}>{t.label}</button>
                      ))}
                      <button className="ac-type-cancel" onClick={() => setAssigningId(null)}>✕</button>
                    </div>
                  ) : (
                    <div className="ac-player-actions">
                      <button className="ac-btn-sm" onClick={() => setAssigningId(p.id)}>Change</button>
                      <button className="ac-btn-sm danger" onClick={() => handleRemove(p.id)}>Remove</button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <h3 className="ac-section-title">General Training</h3>
      <div className="ac-player-list">
        {general.map(p => (
          <div key={p.id} className="ac-player-row">
            <div className="ac-player-info" onClick={() => navigate(`/player/${p.id}`)}>
              <span className="ac-player-name">{p.name}</span>
              <span className="ac-player-role-badge">{ROLE_SHORT[p.role]}</span>
              <span className="ac-player-age">Age {p.age}</span>
            </div>
            <div className="ac-player-training">
              <span className="ac-training-badge general">General</span>
              {assigningId === p.id ? (
                <div className="ac-type-picker">
                  {TRAINING_TYPES.map(t => (
                    <button key={t.value} className="ac-type-opt"
                      onClick={() => handleAssign(p.id, t.value)} title={t.desc}>{t.label}</button>
                  ))}
                  <button className="ac-type-cancel" onClick={() => setAssigningId(null)}>✕</button>
                </div>
              ) : (
                overview.usedFocusedSpots < overview.maxFocusedSpots && (
                  <button className="ac-btn-sm accent" onClick={() => setAssigningId(p.id)}>Focus</button>
                )
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ════════════ Pull History ════════════ */
function PullHistorySection({ data, navigate }) {
  if (!data) return <div className="ac-loading">Loading…</div>;
  if (!data.length) return <div className="ac-empty">No pulls yet</div>;
  return (
    <div className="ac-section">
      <div className="ac-history-list">
        {data.map(p => (
          <div key={p.id} className="ac-history-row" onClick={() => navigate(`/player/${p.player.id}`)}>
            <div className="ac-history-left">
              <span className="ac-history-name">{p.player.name}</span>
              <span className="ac-player-role-badge">{ROLE_SHORT[p.requestedRole]}</span>
              <span className="ac-history-country">{p.pulledFrom}</span>
            </div>
            <div className="ac-history-right">
              <div className="ac-history-ratings">
                <span>B:{p.player.batRating}</span>
                <span>Bw:{p.player.bowlRating}</span>
                {p.player.keeperRating > 0 && <span>WK:{p.player.keeperRating}</span>}
                <span>F:{p.player.fldRating}</span>
              </div>
              {p.pulledAt && <span className="ac-history-date">{new Date(p.pulledAt).toLocaleDateString()}</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ════════════ Training History ════════════ */
function TrainingHistorySection({ data, navigate }) {
  if (!data) return <div className="ac-loading">Loading…</div>;
  if (!data.length) return <div className="ac-empty">No training logs yet</div>;
  return (
    <div className="ac-section">
      <div className="ac-history-list">
        {data.map(log => (
          <div key={log.id} className="ac-history-row" onClick={() => navigate(`/player/${log.playerId}`)}>
            <div className="ac-history-left">
              <span className="ac-history-name">{log.playerName}</span>
              <span className={`ac-training-badge ${log.trainingType === 'GENERAL' ? 'general' : 'focused'}`}>
                {log.trainingType}
              </span>
            </div>
            <div className="ac-history-right">
              <span className="ac-log-skill">{SKILL_LABELS[log.skill] || log.skill}</span>
              <span className="ac-log-values">{log.oldValue} → {log.newValue}</span>
              <span className={`ac-log-change ${log.change > 0 ? 'pop' : 'flop'}`}>
                {log.change > 0 ? `+${log.change}` : log.change}
              </span>
              {log.trainedAt && <span className="ac-history-date">{new Date(log.trainedAt).toLocaleDateString()}</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function StatPill({ label, value }) {
  return (
    <span className="ac-stat-pill">
      <span className="ac-stat-pill-label">{label}</span>
      <span className="ac-stat-pill-value">{value}</span>
    </span>
  );
}
