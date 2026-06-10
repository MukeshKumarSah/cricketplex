import { useParams, useNavigate } from 'react-router-dom';
import { useEffect, useState, useRef, useCallback } from 'react';
import {
  getPlayerProfile, getPlayerTransferStatus,
  listPlayerOnTM, firePlayer, retirePlayer, placeBid,
  cancelListing,
} from '../../api/auth';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../../api/config';
import toast from 'react-hot-toast';
import './Player.css';

function useCountdown(endStr) {
  const [remaining, setRemaining] = useState('');
  useEffect(() => {
    if (!endStr) { setRemaining(''); return; }
    const tick = () => {
      const diff = new Date(endStr + 'Z') - Date.now();
      if (diff <= 0) { setRemaining('Ended'); return; }
      const h = Math.floor(diff / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1000);
      setRemaining(`${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`);
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endStr]);
  return remaining;
}

const ROLE_LABELS = { BATSMAN: 'Batsman', BOWLER: 'Bowler', ALL_ROUNDER: 'All-Rounder', KEEPER: 'Keeper' };
const HAND_LABELS = { RH: 'Right Hand', LH: 'Left Hand' };
const BOWL_LABELS = { FS: 'Finger Spin', WS: 'Wrist Spin', F: 'Fast', M: 'Medium', FM: 'Fast Medium', MF: 'Medium Fast' };
const AGG_LABELS = { D: 'Defensive', N: 'Neutral', A: 'Aggressive' };
const TRAINING_SKILL_LABELS = {
  batRating: 'Bat',
  bowlRating: 'Bowl',
  keeperRating: 'WK',
  fldRating: 'Fld',
  stamina: 'Stam',
  confidence: 'Conf',
};
const FORMAT_ORDER = ['T20', 'ODI', 'FC'];
const FORMAT_COLORS = { T20: '#22d3ee', ODI: '#3b82f6', FC: '#f59e0b' };

function AuctionCountdown({ endStr }) {
  const remaining = useCountdown(endStr);
  if (!remaining) return null;
  const isUrgent = remaining !== 'Ended' && remaining.startsWith('00:0');
  return (
    <span className={`pp-tm-value highlight ${remaining === 'Ended' ? '' : ''}`}
      style={isUrgent ? { color: '#ef4444' } : undefined}>
      {remaining === 'Ended' ? 'Auction Ended' : remaining}
    </span>
  );
}

function RatingBar({ label, value, max = 100, color }) {
  const pct = Math.min((value / max) * 100, 100);
  return (
    <div className="pp-rating-row">
      <span className="pp-rating-label">{label}</span>
      <div className="pp-rating-track">
        <div className="pp-rating-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className="pp-rating-value">{Math.floor(value)}</span>
    </div>
  );
}

export default function Player() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeFormat, setActiveFormat] = useState('T20');
  const [activeType, setActiveType] = useState('LEAGUE');

  // Transfer Market state
  const [tmStatus, setTmStatus] = useState(null);
  const [bidAmount, setBidAmount] = useState('');
  const [startingPrice, setStartingPrice] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [confirmAction, setConfirmAction] = useState(null); // 'fire' | 'retire' | 'sell' | null

  const loadTmStatus = useCallback(() => {
    getPlayerTransferStatus(id)
      .then(res => setTmStatus(res.data))
      .catch(() => {});
  }, [id]);

  // WebSocket — live bid updates
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/transfer', (message) => {
          try {
            const data = JSON.parse(message.body);
            // If this update is about the player we're viewing, refresh TM status
            if (tmStatus?.listingId === data.listingId) {
              loadTmStatus();
            }
          } catch { /* ignore */ }
        });
      },
    });
    client.activate();
    return () => { client.deactivate(); };
  }, [tmStatus?.listingId, loadTmStatus]);

  useEffect(() => {
    (async () => {
      try {
        const res = await getPlayerProfile(id);
        setData(res.data);
        if (res.data.stats) {
          const keys = Object.keys(res.data.stats);
          if (keys.length > 0) {
            const [fmt, type] = keys[0].split('_');
            setActiveFormat(fmt);
            setActiveType(type);
          }
        }
      } catch {
        toast.error('Failed to load player');
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  useEffect(() => { loadTmStatus(); }, [id]);

  const handleSell = async () => {
    const askPrice = Number(startingPrice) || 0;
    setActionLoading(true);
    try {
      const res = await listPlayerOnTM(id, askPrice > 0 ? askPrice : undefined);
      toast.success(res.data.message);
      setConfirmAction(null);
      setStartingPrice('');
      loadTmStatus();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to list');
    } finally { setActionLoading(false); }
  };

  const handleFire = async () => {
    setActionLoading(true);
    try {
      const res = await firePlayer(id);
      toast.success(res.data.message);
      setConfirmAction(null);
      navigate('/squad');
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to fire');
    } finally { setActionLoading(false); }
  };

  const handleRetire = async () => {
    setActionLoading(true);
    try {
      const res = await retirePlayer(id);
      toast.success(res.data.message);
      setConfirmAction(null);
      navigate('/squad');
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to retire');
    } finally { setActionLoading(false); }
  };

  const handleBid = async () => {
    const fallback = tmStatus.minNextBid || tmStatus.currentBid || tmStatus.marketValue;
    const amt = Number(bidAmount || fallback);
    if (!amt || amt <= 0) { toast.error('Enter a valid bid'); return; }
    setActionLoading(true);
    try {
      const res = await placeBid(tmStatus.listingId, amt);
      toast.success(res.data.message);
      setBidAmount('');
      loadTmStatus();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Bid failed');
    } finally { setActionLoading(false); }
  };

  const handleCancelListing = async () => {
    setActionLoading(true);
    try {
      const res = await cancelListing(tmStatus.listingId);
      toast.success(res.data.message);
      loadTmStatus();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Cancel failed');
    } finally { setActionLoading(false); }
  };

  if (loading) return <div className="pp-page"><div className="pp-loading">Loading...</div></div>;
  if (!data) return <div className="pp-page"><div className="pp-empty">Player not found</div></div>;

  const sk = data.skills || {};
  const stats = data.stats || {};
  const trainingHistory = data.trainingHistory || [];

  // Determine if all skills should be visible
  const isFreeAgent = !data.teamId; // Retired/Released players have no team
  const isOnTM = tmStatus?.isListed; // Player is on Transfer Market
  const isOwnPlayer = tmStatus?.isOwnPlayer; // Player belongs to user's team
  const showAllSkills = isOwnPlayer || isFreeAgent || isOnTM;

  // Available format-type combos
  const availableKeys = Object.keys(stats);
  const availableFormats = [...new Set(availableKeys.map(k => k.split('_')[0]))];
  availableFormats.sort((a, b) => FORMAT_ORDER.indexOf(a) - FORMAT_ORDER.indexOf(b));

  const typesForFormat = availableKeys
    .filter(k => k.startsWith(activeFormat + '_'))
    .map(k => k.split('_')[1]);

  const currentKey = activeFormat + '_' + activeType;
  const current = stats[currentKey];

  return (
    <div className="pp-page">
      {/* ─── Header Card ─── */}
      <div className="pp-header">
        <div className="pp-header-top">
          <div className="pp-name-block">
            <h1 className="pp-name">{data.firstName} {data.lastName}</h1>
            <div className="pp-meta-row">
              <span className="pp-role-badge">{ROLE_LABELS[data.role] || data.role}</span>
              <span className="pp-team-link" onClick={() => navigate(`/team/${data.teamId}`)}>{data.teamName}</span>
            </div>
          </div>
          <div className="pp-rating-badge">{data.rating}</div>
        </div>

        <div className="pp-info-grid">
          <div className="pp-info-item"><span className="pp-info-label">Age</span><span className="pp-info-val">{data.age}yr {data.ageDays ?? 0}d</span></div>
          <div className="pp-info-item"><span className="pp-info-label">Country</span><span className="pp-info-val">{data.country}</span></div>
          <div className="pp-info-item"><span className="pp-info-label">Bat</span><span className="pp-info-val">{HAND_LABELS[data.batHand] || data.batHand}</span></div>
          {data.bowlHand && <div className="pp-info-item"><span className="pp-info-label">Bowl</span><span className="pp-info-val">{HAND_LABELS[data.bowlHand]} {BOWL_LABELS[data.bowlType] || data.bowlType}</span></div>}
          <div className="pp-info-item"><span className="pp-info-label">Bat Style</span><span className="pp-info-val">{AGG_LABELS[data.batAggression]}</span></div>
          {data.bowlAggression && <div className="pp-info-item"><span className="pp-info-label">Bowl Style</span><span className="pp-info-val">{AGG_LABELS[data.bowlAggression]}</span></div>}
          <div className="pp-info-item"><span className="pp-info-label">Wage</span><span className="pp-info-val">${data.wage?.toLocaleString()}</span></div>
        </div>
      </div>

      {/* ─── Transfer / Actions ─── */}
      {tmStatus && (
        <div className="pp-section pp-tm-section">
          {/* Market value always visible */}
          <div className="pp-tm-value-row">
            <span className="pp-tm-label">Market Value</span>
            <span className="pp-tm-value">${tmStatus.marketValue?.toLocaleString()}</span>
          </div>

          {/* OWN PLAYER — not listed yet */}
          {tmStatus.isOwnPlayer && !tmStatus.isListed && (
            <>
              {!confirmAction && (
                <div className="pp-tm-actions">
                  <button className="pp-tm-btn sell" onClick={() => setConfirmAction('sell')} disabled={actionLoading}>
                    Sell on TM
                  </button>
                  <button className="pp-tm-btn fire" onClick={() => setConfirmAction('fire')} disabled={actionLoading}>
                    Fire
                  </button>
                  <button className="pp-tm-btn retire" onClick={() => setConfirmAction('retire')} disabled={actionLoading}>
                    Retire
                  </button>
                </div>
              )}
              {confirmAction && (
                <div className="pp-tm-confirm">
                  <span className="pp-tm-confirm-text">
                    {confirmAction === 'sell' && (() => {
                      const base = Number(startingPrice) >= 1000 ? Number(startingPrice) : tmStatus.marketValue;
                      return `List on TM? Listing fee: $${(Math.round(base * 0.20)).toLocaleString()} (20% of starting price)`;
                    })()}
                    {confirmAction === 'fire' && 'Release this player? This cannot be undone.'}
                    {confirmAction === 'retire' && 'Retire this player? This cannot be undone.'}
                  </span>
                  {confirmAction === 'sell' && (
                    <div className="pp-tm-starting-price">
                      <label className="pp-tm-label">Starting Price (optional)</label>
                      <input
                        className="pp-tm-bid-input"
                        type="number"
                        placeholder={`Default: $${tmStatus.marketValue?.toLocaleString()}`}
                        value={startingPrice}
                        onChange={e => setStartingPrice(e.target.value)}
                        min={1000}
                      />
                    </div>
                  )}
                  <div className="pp-tm-confirm-btns">
                    <button className="pp-tm-btn confirm-yes" disabled={actionLoading}
                      onClick={confirmAction === 'sell' ? handleSell : confirmAction === 'fire' ? handleFire : handleRetire}>
                      {actionLoading ? 'Processing…' : 'Confirm'}
                    </button>
                    <button className="pp-tm-btn confirm-no" onClick={() => setConfirmAction(null)}>Cancel</button>
                  </div>
                </div>
              )}
            </>
          )}

          {/* OWN PLAYER — listed, show bids & controls */}
          {tmStatus.isOwnPlayer && tmStatus.isListed && (
            <>
              <div className="pp-tm-listed-badge">Transfer Listed</div>
              <div className="pp-tm-fee-row">
                <span>Listing Fee: <strong>${tmStatus.listingFee?.toLocaleString()}</strong></span>
                <span>TM Tax: <strong>${tmStatus.tmTax?.toLocaleString()}</strong></span>
                <span>Bids: <strong>{tmStatus.bidCount}</strong></span>
              </div>
              {tmStatus.currentBid && tmStatus.currentBidderTeam && (
                <div className="pp-tm-value-row">
                  <span className="pp-tm-label">Current Bid</span>
                  <span className="pp-tm-value highlight">${tmStatus.currentBid?.toLocaleString()} ({tmStatus.currentBidderTeam})</span>
                </div>
              )}
              {tmStatus.auctionEndsAt && (
                <div className="pp-tm-value-row">
                  <span className="pp-tm-label">Ends In</span>
                  <AuctionCountdown endStr={tmStatus.auctionEndsAt} />
                </div>
              )}
              <div className="pp-tm-auction-note">Auction auto-completes when timer expires</div>
              {tmStatus.canCancel && (
                <button className="pp-tm-btn fire" onClick={handleCancelListing} disabled={actionLoading}>
                  Cancel Listing
                </button>
              )}
            </>
          )}

          {/* OTHER PLAYER — listed, show bid input */}
          {!tmStatus.isOwnPlayer && tmStatus.isListed && (
            <>
              <div className="pp-tm-listed-badge">Transfer Listed</div>
              {tmStatus.currentBid && (
                <div className="pp-tm-value-row">
                  <span className="pp-tm-label">Current Bid</span>
                  <span className="pp-tm-value highlight">${tmStatus.currentBid?.toLocaleString()}{tmStatus.currentBidderTeam ? ` (${tmStatus.currentBidderTeam})` : ''}</span>
                </div>
              )}
              {tmStatus.auctionEndsAt && (
                <div className="pp-tm-value-row">
                  <span className="pp-tm-label">Ends In</span>
                  <AuctionCountdown endStr={tmStatus.auctionEndsAt} />
                </div>
              )}
              <div className="pp-tm-bid-row">
                <input
                  className="pp-tm-bid-input"
                  type="number"
                  placeholder={`Min $${(tmStatus.minNextBid || tmStatus.currentBid || tmStatus.marketValue)?.toLocaleString()}`}
                  value={bidAmount || (tmStatus.minNextBid || tmStatus.currentBid || tmStatus.marketValue)}
                  onChange={e => setBidAmount(e.target.value)}
                />
                <button className="pp-tm-btn sell" onClick={handleBid} disabled={actionLoading}>
                  {actionLoading ? 'Bidding…' : 'Place Bid'}
                </button>
              </div>
              <div className="pp-tm-funds">Your Funds: ${tmStatus.myFunds?.toLocaleString()}</div>
            </>
          )}
        </div>
      )}

      {/* ─── Skills ─── */}
      <div className="pp-section">
        <h2 className="pp-section-title">Skills & Attributes</h2>
        <div className="pp-skills-grid">
          <div className="pp-skills-col">
            {showAllSkills && <RatingBar label="BAT" value={sk.batRating} color="#22c55e" />}
            {showAllSkills && <RatingBar label="BOWL" value={sk.bowlRating} color="#3b82f6" />}
            {showAllSkills && <RatingBar label="WK" value={sk.keeperRating} color="#f59e0b" />}
            {showAllSkills && <RatingBar label="FLD" value={sk.fldRating} color="#8b5cf6" />}
            {!showAllSkills && <div className="pp-skills-hidden">Primary skills hidden for players not in your team</div>}
          </div>
          <div className="pp-skills-divider" />
          <div className="pp-skills-col">
            {showAllSkills && <RatingBar label="STA" value={sk.stamina} color="#fb923c" />}
            <RatingBar label="EXP" value={sk.experience} color="#22d3ee" />
            <RatingBar label="CONF" value={sk.confidence} color="#f472b6" />
            <RatingBar label="FIT" value={sk.fitness} color="#a3e635" />
          </div>
        </div>
      </div>

      {/* ─── Stats ─── */}
      {availableFormats.length > 0 && (
        <div className="pp-section">
          <h2 className="pp-section-title">Career Statistics</h2>

          {/* Format tabs */}
          <div className="pp-format-tabs">
            {availableFormats.map(fmt => (
              <button
                key={fmt}
                className={`pp-fmt-tab ${activeFormat === fmt ? 'active' : ''}`}
                style={activeFormat === fmt ? { borderColor: FORMAT_COLORS[fmt], color: FORMAT_COLORS[fmt] } : {}}
                onClick={() => {
                  setActiveFormat(fmt);
                  const types = availableKeys.filter(k => k.startsWith(fmt + '_')).map(k => k.split('_')[1]);
                  setActiveType(types.includes('LEAGUE') ? 'LEAGUE' : types[0]);
                }}
              >{fmt}</button>
            ))}
          </div>

          {/* Type toggle */}
          {typesForFormat.length > 1 && (
            <div className="pp-type-toggle">
              {typesForFormat.map(t => (
                <button
                  key={t}
                  className={`pp-type-btn ${activeType === t ? 'active' : ''}`}
                  onClick={() => setActiveType(t)}
                >{t === 'LEAGUE' ? 'Official' : 'Friendly'}</button>
              ))}
            </div>
          )}
          {typesForFormat.length === 1 && (
            <div className="pp-type-single">{typesForFormat[0] === 'LEAGUE' ? 'Official' : 'Friendly'}</div>
          )}

          {current ? (
            <>
              {/* Batting Stats */}
              {current.batting?.matches > 0 && (
                <div className="pp-stat-block">
                  <h3 className="pp-stat-heading">Batting</h3>
                  <div className="pp-stat-grid">
                    <StatCell label="Mat" value={current.batting.matches} />
                    <StatCell label="Inn" value={current.batting.innings} />
                    <StatCell label="NO" value={current.batting.notOuts} />
                    <StatCell label="Runs" value={current.batting.runs} highlight />
                    <StatCell label="HS" value={current.batting.highest} />
                    <StatCell label="Avg" value={current.batting.average} />
                    <StatCell label="SR" value={current.batting.strikeRate} />
                    <StatCell label="100s" value={current.batting.hundreds} />
                    <StatCell label="50s" value={current.batting.fifties} />
                    <StatCell label="4s" value={current.batting.fours} />
                    <StatCell label="6s" value={current.batting.sixes} />
                  </div>
                </div>
              )}

              {/* Bowling Stats */}
              {current.bowling?.matches > 0 && (
                <div className="pp-stat-block">
                  <h3 className="pp-stat-heading">Bowling</h3>
                  <div className="pp-stat-grid">
                    <StatCell label="Mat" value={current.bowling.matches} />
                    <StatCell label="Inn" value={current.bowling.innings} />
                    <StatCell label="Overs" value={current.bowling.overs} />
                    <StatCell label="Runs" value={current.bowling.runs} />
                    <StatCell label="Wkts" value={current.bowling.wickets} highlight />
                    <StatCell label="Best" value={current.bowling.best} />
                    <StatCell label="Avg" value={current.bowling.average} />
                    <StatCell label="Econ" value={current.bowling.economy} />
                    <StatCell label="SR" value={current.bowling.strikeRate} />
                    <StatCell label="Mdns" value={current.bowling.maidens} />
                    <StatCell label="5W" value={current.bowling.fiveWickets} />
                    <StatCell label="3W" value={current.bowling.threeWickets} />
                  </div>
                </div>
              )}

              {/* Fielding Stats */}
              {current.fielding?.matches > 0 && (
                <div className="pp-stat-block">
                  <h3 className="pp-stat-heading">Fielding</h3>
                  <div className="pp-stat-grid">
                    <StatCell label="Mat" value={current.fielding.matches} />
                    <StatCell label="Ct" value={current.fielding.catches} />
                    <StatCell label="St" value={current.fielding.stumpings} />
                    <StatCell label="RO" value={current.fielding.runOuts} />
                    <StatCell label="Total" value={current.fielding.total} highlight />
                  </div>
                </div>
              )}

              {/* Last 5 */}
              {current.last5?.length > 0 && (
                <div className="pp-stat-block">
                  <h3 className="pp-stat-heading">Last 5 Matches</h3>
                  <div className="pp-last5-list">
                    {current.last5.map((m, i) => (
                      <div key={i} className="pp-last5-row" onClick={() => navigate(`/match/${m.fixtureId}?tab=scorecard`)}>
                        <div className="pp-last5-meta">
                          <span className="pp-last5-vs">vs {m.vs}</span>
                          {m.date && <span className="pp-last5-date">{m.date}</span>}
                        </div>
                        <div className="pp-last5-perf">
                          {m.batInnings?.map((b, j) => (
                            <span key={`bat-${j}`} className="pp-last5-chip bat">
                              {b.runs}{b.notOut ? '*' : ''}<small>({b.balls})</small>
                            </span>
                          ))}
                          {m.bowlInnings?.map((b, j) => (
                            <span key={`bowl-${j}`} className="pp-last5-chip bowl">
                              {b.wickets}/{b.runs}<small>({b.overs}ov)</small>
                            </span>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="pp-no-stats">No stats available for this format</div>
          )}
        </div>
      )}

      <div className="pp-section">
        <h2 className="pp-section-title">Training History</h2>
        {trainingHistory.length === 0 ? (
          <div className="pp-no-stats">No training logs for this player yet</div>
        ) : (
          <div className="pp-training-list">
            {trainingHistory.slice(0, 20).map((log) => (
              <div key={log.id} className="pp-training-row">
                <div className="pp-training-left">
                  <span className={`pp-training-type ${log.trainingType === 'GENERAL' ? 'general' : 'focused'}`}>
                    {log.trainingType}
                  </span>
                  <span className="pp-training-skill">{TRAINING_SKILL_LABELS[log.skill] || log.skill}</span>
                </div>
                <div className="pp-training-right">
                  <span className="pp-training-values">{log.oldValue} {'->'} {log.newValue}</span>
                  <span className={`pp-training-change ${log.change > 0 ? 'up' : 'flat'}`}>
                    {log.change > 0 ? `+${log.change}` : log.change}
                  </span>
                  {log.trainedAt && (
                    <span className="pp-training-date">{new Date(log.trainedAt).toLocaleDateString()}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {availableFormats.length === 0 && (
        <div className="pp-section">
          <div className="pp-no-stats">No match data yet</div>
        </div>
      )}
    </div>
  );
}

function StatCell({ label, value, highlight }) {
  return (
    <div className={`pp-stat-cell ${highlight ? 'highlight' : ''}`}>
      <span className="pp-stat-val">{value ?? '-'}</span>
      <span className="pp-stat-label">{label}</span>
    </div>
  );
}

