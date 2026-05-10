import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  getChallenges,
  getChallengeableTeams,
  sendChallenge,
  acceptChallenge,
  declineChallenge,
  cancelChallenge,
  simulateChallenge,
} from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineBolt,
  HiOutlinePaperAirplane,
  HiOutlineCheck,
  HiOutlineXMark,
  HiOutlinePlayCircle,
  HiOutlineClipboardDocumentList,
  HiOutlineUserGroup,
  HiOutlineTrophy,
} from 'react-icons/hi2';
import './Challenges.css';

const FORMAT_COLORS = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#f59e0b' };
const STATUS_COLORS = {
  PENDING: '#f59e0b',
  ACCEPTED: '#22d3ee',
  DECLINED: '#ef4444',
  CANCELLED: '#6b7280',
  COMPLETED: '#10b981',
  EXPIRED: '#6b7280',
};

const TIME_SLOTS = ['02:00', '07:00', '12:00', '17:00', '21:00'];

const formatTimeSlot = (t) => {
  if (!t) return '';
  const [h, m] = t.split(':');
  const hr = parseInt(h, 10);
  const suffix = hr >= 12 ? 'PM' : 'AM';
  const hr12 = hr === 0 ? 12 : hr > 12 ? hr - 12 : hr;
  return `${hr12}:${m} ${suffix}`;
};



export default function Challenges() {
  const navigate = useNavigate();
  const [challenges, setChallenges] = useState([]);
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState(() => sessionStorage.getItem('challenges_tab') || 'received');
  const [showNewModal, setShowNewModal] = useState(false);
  const [simulating, setSimulating] = useState(null);
  useEffect(() => {
    sessionStorage.setItem('challenges_tab', activeTab);
  }, [activeTab]);

  // New challenge form
  const [selectedTeam, setSelectedTeam] = useState(null);
  const [format, setFormat] = useState('T20');
  const [pitchType, setPitchType] = useState('STANDARD');
  const [matchDate, setMatchDate] = useState('');
  const [matchTime, setMatchTime] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);

  useEffect(() => {
    loadChallenges();
  }, []);

  const loadChallenges = async () => {
    setLoading(true);
    try {
      const res = await getChallenges();
      setChallenges(res.data);
    } catch {
      toast.error('Failed to load challenges');
    } finally {
      setLoading(false);
    }
  };

  const loadTeams = async () => {
    try {
      const res = await getChallengeableTeams();
      setTeams(res.data);
    } catch {
      toast.error('Failed to load teams');
    }
  };

  const openNewChallenge = () => {
    loadTeams();
    setSelectedTeam(null);
    setFormat('T20');
    setPitchType('STANDARD');
    setMatchDate('');
    setMatchTime('');
    setMessage('');
    setShowNewModal(true);
  };

  const handleSend = async () => {
    if (!selectedTeam) return toast.error('Select a team');
    if (!matchDate) return toast.error('Select a match date');
    if (!matchTime) return toast.error('Select a match time');
    setSending(true);
    try {
      await sendChallenge({
        opponentTeamId: selectedTeam,
        format,
        pitchType,
        matchDate,
        matchTime,
        message: message || null,
      });
      toast.success('Challenge sent!');
      setShowNewModal(false);
      loadChallenges();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to send challenge');
    } finally {
      setSending(false);
    }
  };

  const handleAccept = async (id) => {
    try {
      const res = await acceptChallenge(id);
      toast.success(res.data.message);
      loadChallenges();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to accept');
    }
  };

  const handleDecline = async (id) => {
    try {
      await declineChallenge(id);
      toast.success('Challenge declined');
      loadChallenges();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to decline');
    }
  };

  const handleCancel = async (id) => {
    try {
      await cancelChallenge(id);
      toast.success('Challenge cancelled');
      loadChallenges();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to cancel');
    }
  };

  const handleSimulate = async (id) => {
    setSimulating(id);
    try {
      const res = await simulateChallenge(id);
      toast.success(res.data.message);
      if (res.data.fixtureId) {
        navigate(`/match/${res.data.fixtureId}/live`);
      } else {
        loadChallenges();
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Simulation failed');
    } finally {
      setSimulating(null);
    }
  };

  // Accepted/completed challenges show on the Matches page, not here
  const activeChallenges = challenges.filter((c) => c.status !== 'ACCEPTED' && c.status !== 'COMPLETED');
  const received = activeChallenges.filter((c) => c.isReceiver);
  const sent = activeChallenges.filter((c) => c.isSender);
  const displayed = activeTab === 'received' ? received : sent;

  const today = new Date().toISOString().split('T')[0];

  // Generate next 10 days as date options
  const dateOptions = Array.from({ length: 11 }, (_, i) => {
    const d = new Date(Date.now() + i * 86400000);
    return {
      value: d.toISOString().split('T')[0],
      label: d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' }),
    };
  });

  // Filter time slots: if selected date is today, only show future times
  const nowUtcHours = new Date().getUTCHours();
  const nowUtcMinutes = new Date().getUTCMinutes();
  const availableTimeSlots = TIME_SLOTS.filter((t) => {
    if (matchDate !== today) return true;
    const [h, m] = t.split(':').map(Number);
    return h > nowUtcHours || (h === nowUtcHours && m > nowUtcMinutes);
  });

  return (
    <div className="challenges-page">
      {/* Header */}
      <div className="challenges-header">
        <HiOutlineBolt className="challenges-header-icon" />
        <div>
          <h1>Friendly Challenges</h1>
          <p className="challenges-subtitle">Challenge other managers to friendly matches</p>
        </div>
        <button className="challenges-new-btn" onClick={openNewChallenge}>
          <HiOutlinePaperAirplane /> New Challenge
        </button>
      </div>

      {/* Tabs */}
      <div className="challenges-tabs">
        <button
          className={`challenges-tab tab-received ${activeTab === 'received' ? 'active' : ''}`}
          onClick={() => setActiveTab('received')}
        >
          Received ({received.filter((c) => c.status === 'PENDING').length})
        </button>
        <button
          className={`challenges-tab tab-sent ${activeTab === 'sent' ? 'active' : ''}`}
          onClick={() => setActiveTab('sent')}
        >
          Sent ({sent.filter((c) => c.status === 'PENDING').length})
        </button>
      </div>

      {/* Challenge List */}
      <div className="challenges-list">
        {loading ? (
          <div className="challenges-empty">Loading...</div>
        ) : displayed.length === 0 ? (
          <div className="challenges-empty">
            {activeTab === 'received'
              ? 'No challenges received yet.'
              : 'No challenges sent yet.'}
          </div>
        ) : (
          displayed.map((c) => (
            <div key={c.id} className="challenge-card">
              <div className="challenge-card-top">
                <div className="challenge-teams">
                  <div className="challenge-team">
                    {c.challengerTeamPic ? (
                      <img
                        src={`/api/files/${c.challengerTeamPic}`}
                        alt=""
                        className="challenge-team-logo"
                      />
                    ) : (
                      <span className="challenge-team-initials">
                        {c.challengerTeamName?.slice(0, 2).toUpperCase()}
                      </span>
                    )}
                    <span className="challenge-team-name">{c.challengerTeamName}</span>
                  </div>
                  <span className="challenge-vs">vs</span>
                  <div className="challenge-team">
                    {c.challengedTeamPic ? (
                      <img
                        src={`/api/files/${c.challengedTeamPic}`}
                        alt=""
                        className="challenge-team-logo"
                      />
                    ) : (
                      <span className="challenge-team-initials">
                        {c.challengedTeamName?.slice(0, 2).toUpperCase()}
                      </span>
                    )}
                    <span className="challenge-team-name">{c.challengedTeamName}</span>
                  </div>
                </div>

                <div className="challenge-meta">
                  <span
                    className="challenge-format"
                    style={{
                      color: FORMAT_COLORS[c.format] || '#94a3b8',
                      borderColor: (FORMAT_COLORS[c.format] || '#94a3b8') + '40',
                      background: (FORMAT_COLORS[c.format] || '#94a3b8') + '18',
                    }}
                  >
                    {c.format}
                  </span>
                  <span className="challenge-date">
                    {new Date(c.matchDate + 'T00:00:00').toLocaleDateString('en-US', {
                      weekday: 'short',
                      month: 'short',
                      day: 'numeric',
                    })}
                    {c.matchTime && ` · ${formatTimeSlot(c.matchTime)} UTC`}
                  </span>
                  <span className="challenge-pitch">{c.pitchType}</span>
                  <span
                    className="challenge-status"
                    style={{ color: c.fixtureStatus === 'IN_PROGRESS' ? '#f59e0b' : (STATUS_COLORS[c.status] || '#94a3b8') }}
                  >
                    {c.fixtureStatus === 'IN_PROGRESS' ? 'LIVE' : c.status}
                  </span>
                </div>
              </div>

              {c.message && <div className="challenge-message">"{c.message}"</div>}

              {/* Lineup status for accepted challenges */}
              {c.status === 'ACCEPTED' && (
                <div className="challenge-lineup-status">
                  <span className={c.myLineupSet ? 'lineup-set' : 'lineup-pending'}>
                    {c.myLineupSet ? '✓ Your lineup set' : '○ Your lineup pending'}
                  </span>
                  <span className={c.opponentLineupSet ? 'lineup-set' : 'lineup-pending'}>
                    {c.opponentLineupSet ? '✓ Opponent lineup set' : '○ Opponent lineup pending'}
                  </span>
                </div>
              )}

              {/* Actions */}
              <div className="challenge-actions">
                {/* Received + Pending (not expired) → Accept / Decline */}
                {c.isReceiver && c.status === 'PENDING' && !c.expired && (
                  <>
                    <button className="ch-btn ch-accept" onClick={() => handleAccept(c.id)}>
                      <HiOutlineCheck /> Accept
                    </button>
                    <button className="ch-btn ch-decline" onClick={() => handleDecline(c.id)}>
                      <HiOutlineXMark /> Decline
                    </button>
                  </>
                )}

                {/* Expired PENDING → message */}
                {c.status === 'PENDING' && c.expired && (
                  <span className="challenge-expired-msg">
                    {c.isSender ? 'Opponent didn\'t accept' : 'You missed to accept this challenge'}
                  </span>
                )}

                {/* EXPIRED status (set by scheduler) → message */}
                {c.status === 'EXPIRED' && (
                  <span className="challenge-expired-msg">
                    {c.isSender ? 'Opponent didn\'t accept' : 'You missed to accept this challenge'}
                  </span>
                )}

                {/* Sent + Pending (not expired) → Cancel */}
                {c.isSender && c.status === 'PENDING' && !c.expired && (
                  <button className="ch-btn ch-cancel" onClick={() => handleCancel(c.id)}>
                    <HiOutlineXMark /> Cancel
                  </button>
                )}

                {/* Accepted → Set Lineup */}
                {c.status === 'ACCEPTED' && c.fixtureId && !c.myLineupSet && (
                  <button
                    className="ch-btn ch-lineup"
                    onClick={() => navigate(`/match/${c.fixtureId}/lineup`)}
                  >
                    <HiOutlineUserGroup /> Set Lineup
                  </button>
                )}

                {/* Both lineups set, not yet started → show "Starts at" info */}
                {c.status === 'ACCEPTED' && c.bothLineupsSet && !c.resultExists && (
                  <span className="challenge-starts-at">
                    Match starts at {formatTimeSlot(c.matchTime)} UTC
                  </span>
                )}

                {/* Completed → View Scorecard / Continue Watching */}
                {c.resultExists && c.fixtureId && (
                  c.fixtureStatus === 'IN_PROGRESS' ? (
                    <button
                      className="ch-btn ch-simulate"
                      onClick={() => navigate(`/match/${c.fixtureId}/live`)}
                    >
                      <HiOutlinePlayCircle /> Watch Live
                    </button>
                  ) : (
                    <>
                      <button
                        className="ch-btn ch-scorecard"
                        onClick={() => navigate(`/match/${c.fixtureId}/scorecard`)}
                      >
                        <HiOutlineTrophy /> Scorecard
                      </button>
                      <button
                        className="ch-btn ch-commentary"
                        onClick={() => navigate(`/match/${c.fixtureId}/commentary`)}
                      >
                        <HiOutlineClipboardDocumentList /> Commentary
                      </button>
                    </>
                  )
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {/* ── New Challenge Modal ── */}
      {showNewModal && (
        <div className="challenge-modal-backdrop" onClick={() => setShowNewModal(false)}>
          <div className="challenge-modal" onClick={(e) => e.stopPropagation()}>
            <h2>New Friendly Challenge</h2>

            <div className="cm-field">
              <label>Opponent Team</label>
              <select
                value={selectedTeam || ''}
                onChange={(e) => setSelectedTeam(e.target.value || null)}
              >
                <option value="">Select a team...</option>
                {teams.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.teamName} ({t.ownerName}) — {t.country}
                  </option>
                ))}
              </select>
            </div>

            <div className="cm-row">
              <div className="cm-field">
                <label>Format</label>
                <select value={format} onChange={(e) => setFormat(e.target.value)}>
                  <option value="T20">T20</option>
                  <option value="ODI">One Day</option>
                  <option value="FC">First Class</option>
                </select>
              </div>
              <div className="cm-field">
                <label>Pitch Type</label>
                <select value={pitchType} onChange={(e) => setPitchType(e.target.value)}>
                  <option value="STANDARD">Standard</option>
                  <option value="FLAT">Flat</option>
                  <option value="GREEN">Green</option>
                  <option value="DUSTY">Dusty</option>
                  <option value="DRY">Dry</option>
                  <option value="BOUNCY">Bouncy</option>
                  <option value="SLOW">Slow</option>
                  <option value="UNEVEN">Uneven</option>
                </select>
              </div>
            </div>

            <div className="cm-row">
              <div className="cm-field">
                <label>Match Date</label>
                <select value={matchDate} onChange={(e) => { setMatchDate(e.target.value); setMatchTime(''); }}>
                  <option value="">Select a date...</option>
                  {dateOptions.map((d) => (
                    <option key={d.value} value={d.value}>{d.label}</option>
                  ))}
                </select>
              </div>
              <div className="cm-field">
                <label>Match Time (UTC)</label>
                <select value={matchTime} onChange={(e) => setMatchTime(e.target.value)} disabled={!matchDate}>
                  <option value="">Select a time...</option>
                  {availableTimeSlots.map((t) => (
                    <option key={t} value={t}>{formatTimeSlot(t)}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="cm-field">
              <label>Message (optional)</label>
              <input
                type="text"
                maxLength={200}
                placeholder="Let's go!"
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />
            </div>

            <div className="cm-actions">
              <button className="cm-cancel" onClick={() => setShowNewModal(false)}>Cancel</button>
              <button className="cm-send" onClick={handleSend} disabled={sending}>
                {sending ? 'Sending...' : 'Send Challenge'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
