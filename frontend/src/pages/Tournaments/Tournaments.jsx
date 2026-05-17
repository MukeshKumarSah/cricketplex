import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  listTournaments,
  createTournament,
  joinTournamentByCode,
  respondTournamentInvite,
} from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlinePlus,
  HiOutlineKey,
  HiOutlineUserGroup,
  HiOutlineCalendarDays,
  HiOutlineGlobeAlt,
  HiOutlineLockClosed,
  HiOutlineCheckCircle,
  HiOutlineXCircle,
} from 'react-icons/hi2';
import './Tournaments.css';

const FILTER_TABS = [
  { key: 'public',  label: 'Public' },
  { key: 'mine',    label: 'My Tournaments' },
  { key: 'joined',  label: 'Joined' },
  { key: 'pending', label: 'Invites' },
];

export default function Tournaments() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ADMIN';
  const isSupporter = user?.isSupporter;
  const canCreate = isAdmin || isSupporter;

  const [filter, setFilter] = useState('public');
  const [tournaments, setTournaments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [showJoin, setShowJoin] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [joining, setJoining] = useState(false);

  const [form, setForm] = useState({
    name: '',
    type: 'LEAGUE',
    format: 'T20',
    scheduleType: 'WEEKLY',
    scheduleDays: 'SATURDAY',
    isPublic: true,
    startDate: '',
  });
  const [creating, setCreating] = useState(false);

  const load = () => {
    setLoading(true);
    listTournaments(filter)
      .then((r) => setTournaments(r.data))
      .catch(() => toast.error('Failed to load tournaments'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [filter]); // eslint-disable-line

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      const payload = {
        ...form,
        isPublic: form.isPublic === true || form.isPublic === 'true',
        startDate: form.startDate || undefined,
      };
      const res = await createTournament(payload);
      toast.success('Tournament created!');
      setShowCreate(false);
      navigate(`tournaments/${res.data.id}`);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to create tournament');
    } finally {
      setCreating(false);
    }
  };

  const handleJoin = async (e) => {
    e.preventDefault();
    if (!joinCode.trim()) return;
    setJoining(true);
    try {
      await joinTournamentByCode(joinCode.trim());
      toast.success('Joined tournament!');
      setShowJoin(false);
      setJoinCode('');
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Invalid join code');
    } finally {
      setJoining(false);
    }
  };

  const handleRespond = async (tournamentId, accept) => {
    try {
      await respondTournamentInvite(tournamentId, accept);
      toast.success(accept ? 'Invite accepted!' : 'Invite declined');
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to respond');
    }
  };

  const statusColor = (status) => {
    if (status === 'REGISTRATION') return 'var(--color-amber)';
    if (status === 'ACTIVE') return 'var(--color-green)';
    if (status === 'COMPLETED') return 'var(--text-3)';
    return 'var(--color-red)';
  };

  return (
    <div className="tournaments-page">
      <div className="tournaments-header">
        <div className="tournaments-title-row">
          <HiOutlineTrophy className="tournaments-icon" />
          <h1>Friendly Tournaments</h1>
        </div>
        <div className="tournaments-actions">
          <button className="btn-secondary" onClick={() => setShowJoin(true)}>
            <HiOutlineKey /> Join by Code
          </button>
          {canCreate && (
            <button className="btn-primary" onClick={() => setShowCreate(true)}>
              <HiOutlinePlus /> Create Tournament
            </button>
          )}
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="filter-tabs">
        {FILTER_TABS.map((t) => (
          <button
            key={t.key}
            className={`filter-tab${filter === t.key ? ' active' : ''}`}
            onClick={() => setFilter(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tournament List */}
      {loading ? (
        <div className="tournaments-loading">Loading tournaments…</div>
      ) : tournaments.length === 0 ? (
        <div className="tournaments-empty">
          {filter === 'pending' ? 'No pending invites.' : 'No tournaments found.'}
        </div>
      ) : (
        <div className="tournaments-grid">
          {tournaments.map((t) => (
            <div key={t.id} className="tournament-card" onClick={() => navigate(`/tournaments/${t.id}`)}>
              <div className="tc-header">
                <span className="tc-type-badge" data-type={t.type}>{t.type}</span>
                <span className="tc-format">{t.format}</span>
                {t.isPublic
                  ? <HiOutlineGlobeAlt className="tc-vis-icon" title="Public" />
                  : <HiOutlineLockClosed className="tc-vis-icon" title="Private" />}
              </div>
              <h3 className="tc-name">{t.name}</h3>
              <div className="tc-meta">
                <span style={{ color: statusColor(t.status) }}>● {t.status}</span>
                <span><HiOutlineUserGroup /> {t.acceptedTeams} teams</span>
                {t.startDate && <span><HiOutlineCalendarDays /> {new Date(t.startDate).toLocaleDateString()}</span>}
              </div>
              <div className="tc-creator">By {t.createdBy}</div>

              {filter === 'pending' && (
                <div className="tc-respond-row" onClick={(e) => e.stopPropagation()}>
                  <button className="btn-accept" onClick={() => handleRespond(t.id, true)}>
                    <HiOutlineCheckCircle /> Accept
                  </button>
                  <button className="btn-decline" onClick={() => handleRespond(t.id, false)}>
                    <HiOutlineXCircle /> Decline
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {showCreate && (
        <div className="modal-overlay" onClick={() => setShowCreate(false)}>
          <div className="modal-box" onClick={(e) => e.stopPropagation()}>
            <h2>Create Tournament</h2>
            <form onSubmit={handleCreate} className="create-form">
              <label>Name
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
              </label>
              <div className="form-row">
                <label>Type
                  <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                    <option value="LEAGUE">League (4–8 teams)</option>
                    <option value="KNOCKOUT">Knockout (8+ teams)</option>
                  </select>
                </label>
                <label>Format
                  <select value={form.format} onChange={(e) => setForm({ ...form, format: e.target.value })}>
                    <option value="T20">T20</option>
                    <option value="ODI">ODI</option>
                  </select>
                </label>
              </div>
              <div className="form-row">
                <label>Schedule
                  <select value={form.scheduleType} onChange={(e) => setForm({ ...form, scheduleType: e.target.value })}>
                    <option value="WEEKLY">Weekly (1/week)</option>
                    <option value="BIWEEKLY">Bi-weekly (2/week)</option>
                    <option value="TRIWEEKLY">Tri-weekly (3/week)</option>
                  </select>
                </label>
                <label>Match Day(s)
                  <input
                    placeholder="e.g. SATURDAY or MON,THU"
                    value={form.scheduleDays}
                    onChange={(e) => setForm({ ...form, scheduleDays: e.target.value })}
                  />
                </label>
              </div>
              <label>Start Date
                <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
              </label>
              <label className="check-label">
                <input type="checkbox" checked={form.isPublic} onChange={(e) => setForm({ ...form, isPublic: e.target.checked })} />
                Public (anyone can join with code)
              </label>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn-primary" disabled={creating}>
                  {creating ? 'Creating…' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Join by Code Modal */}
      {showJoin && (
        <div className="modal-overlay" onClick={() => setShowJoin(false)}>
          <div className="modal-box modal-sm" onClick={(e) => e.stopPropagation()}>
            <h2>Join by Code</h2>
            <form onSubmit={handleJoin}>
              <label>Join Code
                <input
                  placeholder="e.g. AB3K9M2Z"
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                  maxLength={8}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowJoin(false)}>Cancel</button>
                <button type="submit" className="btn-primary" disabled={joining}>
                  {joining ? 'Joining…' : 'Join'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
