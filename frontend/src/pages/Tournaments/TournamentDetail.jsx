import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  getTournamentDetail,
  inviteTeamToTournament,
  startTournament,
  advanceKnockoutRound,
  cancelTournament,
  getTeamList,
} from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineCalendarDays,
  HiOutlineTableCells,
  HiOutlineQueueList,
  HiOutlineUserGroup,
  HiOutlinePlay,
  HiOutlineArrowRight,
  HiOutlineXMark,
  HiOutlinePlus,
  HiOutlineKey,
} from 'react-icons/hi2';
import './Tournaments.css';

const TABS_LEAGUE   = ['Fixtures', 'Standings', 'Teams'];
const TABS_KNOCKOUT = ['Bracket', 'Teams'];

export default function TournamentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('');

  const [showInvite, setShowInvite] = useState(false);
  const [allTeams, setAllTeams] = useState([]);
  const [inviteSearch, setInviteSearch] = useState('');
  const [inviting, setInviting] = useState(false);

  const load = () => {
    setLoading(true);
    getTournamentDetail(id)
      .then((r) => {
        setData(r.data);
        if (!activeTab) {
          setActiveTab(r.data.type === 'LEAGUE' ? 'Fixtures' : 'Bracket');
        }
      })
      .catch(() => toast.error('Failed to load tournament'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [id]); // eslint-disable-line

  const openInviteModal = () => {
    getTeamList().then((r) => setAllTeams(r.data.filter(t => !t.isBot))).catch(() => {});
    setShowInvite(true);
  };

  const handleInvite = async (teamId) => {
    setInviting(true);
    try {
      await inviteTeamToTournament(id, teamId);
      toast.success('Team invited');
      load();
      setShowInvite(false);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to invite');
    } finally {
      setInviting(false);
    }
  };

  const handleStart = async () => {
    if (!window.confirm('Start tournament and generate fixtures?')) return;
    try {
      await startTournament(id);
      toast.success('Tournament started!');
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to start');
    }
  };

  const handleAdvance = async () => {
    if (!window.confirm('Advance to the next knockout round?')) return;
    try {
      await advanceKnockoutRound(id);
      toast.success('Next round generated!');
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to advance');
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Cancel this tournament?')) return;
    try {
      await cancelTournament(id);
      toast.success('Tournament cancelled');
      navigate('/tournaments');
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to cancel');
    }
  };

  if (loading) return <div className="tournaments-loading">Loading…</div>;
  if (!data) return null;

  const isCreator = data.isCreator;
  const canManage = isCreator || isAdmin;
  const isLeague = data.type === 'LEAGUE';
  const tabs = isLeague ? TABS_LEAGUE : TABS_KNOCKOUT;

  const acceptedTeams = data.teams?.filter((t) => t.inviteStatus === 'ACCEPTED') || [];
  const pendingTeams  = data.teams?.filter((t) => t.inviteStatus === 'INVITED') || [];

  const fixturesByRound = (data.fixtures || []).reduce((acc, f) => {
    const key = isLeague ? `Leg ${f.leg || 1}, Round ${f.round}` : (f.roundName || `Round ${f.round}`);
    (acc[key] = acc[key] || []).push(f);
    return acc;
  }, {});

  const filteredTeams = allTeams.filter(
    (t) =>
      t.teamName?.toLowerCase().includes(inviteSearch.toLowerCase()) &&
      !data.teams?.some((dt) => dt.teamId === t.id),
  );

  const statusDot = {
    REGISTRATION: '🟡',
    ACTIVE: '🟢',
    COMPLETED: '⚫',
    CANCELLED: '🔴',
  };

  return (
    <div className="tournaments-page">
      {/* Header */}
      <div className="td-header">
        <button className="btn-back" onClick={() => navigate('/tournaments')}>← Back</button>
        <div className="td-title-row">
          <HiOutlineTrophy className="tournaments-icon" />
          <div>
            <h1>{data.name}</h1>
            <p className="td-meta">
              {statusDot[data.status]} {data.status} &nbsp;·&nbsp;
              {data.type} &nbsp;·&nbsp; {data.format} &nbsp;·&nbsp;
              By {data.createdBy?.name}
              {data.joinCode && canManage && (
                <span className="td-code"><HiOutlineKey /> {data.joinCode}</span>
              )}
            </p>
          </div>
        </div>

        {/* Action buttons */}
        {canManage && (
          <div className="td-actions">
            {data.status === 'REGISTRATION' && (
              <>
                <button className="btn-secondary" onClick={openInviteModal}>
                  <HiOutlinePlus /> Invite Team
                </button>
                <button className="btn-primary" onClick={handleStart}>
                  <HiOutlinePlay /> Start Tournament
                </button>
              </>
            )}
            {data.status === 'ACTIVE' && !isLeague && (
              <button className="btn-primary" onClick={handleAdvance}>
                <HiOutlineArrowRight /> Advance Round
              </button>
            )}
            {(data.status === 'REGISTRATION' || data.status === 'ACTIVE') && (
              <button className="btn-danger" onClick={handleCancel}>
                <HiOutlineXMark /> Cancel
              </button>
            )}
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="filter-tabs">
        {tabs.map((t) => (
          <button
            key={t}
            className={`filter-tab${activeTab === t ? ' active' : ''}`}
            onClick={() => setActiveTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {/* TAB: Fixtures / Bracket */}
      {(activeTab === 'Fixtures' || activeTab === 'Bracket') && (
        <div className="fixtures-section">
          {Object.keys(fixturesByRound).length === 0 ? (
            <p className="tournaments-empty">No fixtures yet — start the tournament to generate them.</p>
          ) : (
            Object.entries(fixturesByRound).map(([roundLabel, fixtures]) => (
              <div key={roundLabel} className="fixture-round">
                <h3 className="round-label">{roundLabel}</h3>
                <div className="fixture-list">
                  {fixtures.map((f) => (
                    <div
                      key={f.id}
                      className={`fixture-row ${f.status === 'COMPLETED' ? 'completed' : ''}`}
                      onClick={() => navigate(`/match/${f.id}/preview`)}
                    >
                      <span className="fx-team home">{f.homeTeam?.name}</span>
                      <div className="fx-center">
                        {f.status === 'COMPLETED' && f.winner ? (
                          <span className="fx-result">
                            {f.winner.name === f.homeTeam?.name ? '✓ · ·' : '· · ✓'}
                          </span>
                        ) : (
                          <span className="fx-vs">vs</span>
                        )}
                        <span className="fx-date">
                          {f.matchDate ? new Date(f.matchDate).toLocaleDateString() : ''}
                        </span>
                      </div>
                      <span className="fx-team away">{f.awayTeam?.name}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* TAB: Standings (league only) */}
      {activeTab === 'Standings' && (
        <div className="standings-section">
          {!data.standings || data.standings.length === 0 ? (
            <p className="tournaments-empty">Standings will appear once the tournament starts.</p>
          ) : (
            <table className="standings-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Team</th>
                  <th>P</th>
                  <th>W</th>
                  <th>D</th>
                  <th>L</th>
                  <th>Pts</th>
                  <th>NRR</th>
                </tr>
              </thead>
              <tbody>
                {data.standings.map((s, i) => (
                  <tr key={s.teamId}>
                    <td>{i + 1}</td>
                    <td className="team-name-cell">{s.teamName}</td>
                    <td>{s.played}</td>
                    <td>{s.won}</td>
                    <td>{s.drawn}</td>
                    <td>{s.lost}</td>
                    <td className="pts-cell">{s.points}</td>
                    <td className={s.nrr >= 0 ? 'nrr-pos' : 'nrr-neg'}>
                      {s.nrr >= 0 ? '+' : ''}{s.nrr?.toFixed(3)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* TAB: Teams */}
      {activeTab === 'Teams' && (
        <div className="teams-section">
          {acceptedTeams.length > 0 && (
            <>
              <h3>Accepted ({acceptedTeams.length})</h3>
              <div className="team-chips">
                {acceptedTeams.map((t) => (
                  <span key={t.teamId} className="team-chip accepted">{t.teamName}</span>
                ))}
              </div>
            </>
          )}
          {pendingTeams.length > 0 && (
            <>
              <h3>Pending Invites ({pendingTeams.length})</h3>
              <div className="team-chips">
                {pendingTeams.map((t) => (
                  <span key={t.teamId} className="team-chip pending">{t.teamName}</span>
                ))}
              </div>
            </>
          )}
          {data.teams?.length === 0 && (
            <p className="tournaments-empty">No teams yet.</p>
          )}
        </div>
      )}

      {/* Invite Modal */}
      {showInvite && (
        <div className="modal-overlay" onClick={() => setShowInvite(false)}>
          <div className="modal-box" onClick={(e) => e.stopPropagation()}>
            <h2>Invite a Team</h2>
            <input
              className="invite-search"
              placeholder="Search teams…"
              value={inviteSearch}
              onChange={(e) => setInviteSearch(e.target.value)}
            />
            <div className="invite-team-list">
              {filteredTeams.slice(0, 30).map((t) => (
                <button
                  key={t.id}
                  className="invite-team-row"
                  onClick={() => handleInvite(t.id)}
                  disabled={inviting}
                >
                  {t.teamName}
                  <span className="invite-action">Invite</span>
                </button>
              ))}
              {filteredTeams.length === 0 && <p>No teams found.</p>}
            </div>
            <button className="btn-secondary" style={{ marginTop: '1rem', width: '100%' }} onClick={() => setShowInvite(false)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}
