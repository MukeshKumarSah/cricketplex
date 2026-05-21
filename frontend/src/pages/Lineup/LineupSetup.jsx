import { useState, useEffect, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getLineupData, getFCState, saveLineup as saveLineupApi, saveFCStrategy } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineCalendarDays,
  HiOutlineMapPin,
  HiOutlineUserGroup,
  HiOutlineChevronUp,
  HiOutlineChevronDown,
} from 'react-icons/hi2';
import './LineupSetup.css';

const ROLE_SHORT = { BATSMAN: 'BAT', KEEPER: 'WK', ALL_ROUNDER: 'AR', BOWLER: 'BOWL' };
const ROLE_LABELS = { BATSMAN: 'Batsman', KEEPER: 'Keeper', ALL_ROUNDER: 'All-Rounder', BOWLER: 'Bowler' };
const BOWL_TYPE_LABELS = { FS: 'Finger Spin', WS: 'Wrist Spin', F: 'Fast', M: 'Medium', FM: 'Fast-Medium', MF: 'Medium-Fast' };
const AGG_OPTIONS = [
  { value: 'D', label: 'Defensive' },
  { value: 'N', label: 'Neutral' },
  { value: 'A', label: 'Aggressive' },
];

const FORMAT_COLORS = { T20: '#22d3ee', ODI: '#a78bfa', FC: '#34d399' };

/* ─── Bowling Templates ─── */
// T20: 20 overs, 5 bowlers, each 4, no consecutive
const T20_TEMPLATES = {
  BALANCED:   [1,2,3,4,5,1,2,3,4,5,1,2,3,4,5,1,2,3,4,5],
  PACE_HEAVY: [1,2,1,3,2,4,5,4,3,5,1,4,5,3,2,1,5,4,3,2],
  SPIN_HEAVY: [1,3,2,4,1,3,5,4,3,5,2,4,5,1,4,2,5,3,1,2],
};
// ODI: 50 overs, 5 bowlers, each 10, no consecutive
const ODI_TEMPLATES = {
  BALANCED:   Array.from({ length: 50 }, (_, i) => (i % 5) + 1),
  PACE_HEAVY: [1,2,1,3,2,1,4,2,3,5, 4,3,5,4,3,5,4,3,5,3, 4,5,3,4,5,3,4,5,3,5, 4,3,5,1,2,1,3,2,5,4, 1,2,1,2,1,2,1,2,3,4],
  SPIN_HEAVY: [1,3,2,4,1,5,2,3,4,5, 3,4,5,3,4,5,3,4,5,3, 4,5,3,4,5,3,4,5,1,2, 3,4,5,1,3,2,4,5,1,2, 1,2,1,2,5,1,2,3,4,5],
};
// FC: no templates — custom 100-over plans
const BOWLER_COLORS = ['#22d3ee', '#4ade80', '#fbbf24', '#c084fc', '#f472b6', '#fb923c', '#a78bfa', '#34d399', '#f87171', '#60a5fa', '#e879f9'];

const TEMPLATE_LABELS = {
  BALANCED: 'Balanced',
  PACE_HEAVY: 'Pace Heavy',
  SPIN_HEAVY: 'Spin Heavy',
  CUSTOM: 'Custom',
};

export default function LineupSetup() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [matchInfo, setMatchInfo] = useState(null);
  const [squad, setSquad] = useState([]);

  // Lineup state
  const [playing11, setPlaying11] = useState(Array(11).fill(null)); // array of player IDs
  const [batAggression, setBatAggression] = useState(Array(11).fill('N'));
  const [captainId, setCaptainId] = useState(null);
  const [keeperId, setKeeperId] = useState(null);

  // Toss
  const [tossChoice, setTossChoice] = useState(null);
  const [batOrBowl, setBatOrBowl] = useState(null);

  // Bowling
  const [bowlingPlan, setBowlingPlan] = useState('BALANCED');
  const [selectedBowlers, setSelectedBowlers] = useState(Array(5).fill(null)); // bowler IDs (5 for T20/ODI, dynamic for FC)
  const [customBowling, setCustomBowling] = useState([]); // [{bowlerId, aggression}] indexed by over
  const customBowlingBackup = useRef(null); // backs up custom state when switching to a template

  // FC-specific: per-bowler aggression (default brush) and active brush
  const [bowlerAggression, setBowlerAggression] = useState(Array(5).fill('N'));
  const [activeBrush, setActiveBrush] = useState(0); // 0-N = bowler slot, -1 = eraser

  // FC Strategy
  const [fcDeclareInn1, setFcDeclareInn1] = useState('');
  const [fcDeclareInn2Lead, setFcDeclareInn2Lead] = useState('');
  const [fcFollowOn, setFcFollowOn] = useState(true);
  const [fcDeclareInn3Lead, setFcDeclareInn3Lead] = useState('');

  // Sorting for player pool
  const [sortKey, setSortKey] = useState('rating');
  const [sortDir, setSortDir] = useState('desc');
  const [saveAsDefault, setSaveAsDefault] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const res = await getLineupData(fixtureId);
        setMatchInfo(res.data.matchInfo);
        setSquad(res.data.squad);

        const format = res.data.matchInfo.format;
        const totalOvers = format === 'T20' ? 20 : format === 'ODI' ? 50 : 100;
        setCustomBowling(Array.from({ length: totalOvers }, () => ({ bowlerId: null, aggression: 'N' })));
        if (format === 'FC') setBowlingPlan('CUSTOM');

        if (format === 'FC') {
          try {
            const fcRes = await getFCState(fixtureId);
            const fc = fcRes.data;
            if (fc?.found) {
              if (fc.declareInn1 != null) setFcDeclareInn1(String(fc.declareInn1));
              if (fc.declareInn2Lead != null) setFcDeclareInn2Lead(String(fc.declareInn2Lead));
              if (fc.followOn != null) setFcFollowOn(fc.followOn);
              if (fc.declareInn3Lead != null) setFcDeclareInn3Lead(String(fc.declareInn3Lead));
            }
          } catch {
            // FC strategy is optional during lineup load; ignore failures here
          }
        }

        // Restore saved lineup
        const sl = res.data.savedLineup || res.data.defaultLineup;
        if (sl) {
          if (sl.captainId) setCaptainId(sl.captainId);
          if (sl.keeperId) setKeeperId(sl.keeperId);
          if (sl.tossChoice) setTossChoice(sl.tossChoice);
          if (sl.batOrBowl) setBatOrBowl(sl.batOrBowl);
          if (sl.bowlingPlan) setBowlingPlan(sl.bowlingPlan);

          if (sl.players && sl.players.length > 0) {
            const newPlaying = Array(11).fill(null);
            const newAgg = Array(11).fill('N');
            sl.players.forEach((p) => {
              const idx = p.battingPosition - 1;
              if (idx >= 0 && idx < 11) {
                newPlaying[idx] = p.playerId;
                newAgg[idx] = p.batAggression || 'N';
              }
            });
            setPlaying11(newPlaying);
            setBatAggression(newAgg);
          }

          if (sl.bowlingOrders && sl.bowlingOrders.length > 0) {
            // Always restore the over-by-over grid
            const cb = Array.from({ length: totalOvers }, () => ({ bowlerId: null, aggression: 'N' }));
            sl.bowlingOrders.forEach((bo) => {
              const idx = bo.overNumber - 1;
              if (idx >= 0 && idx < totalOvers) {
                cb[idx] = { bowlerId: bo.bowlerId, aggression: bo.aggression || 'N' };
              }
            });
            setCustomBowling(cb);

            // Also derive bowler slots from saved orders
            const bowlerMap = {};
            sl.bowlingOrders.forEach((bo) => {
              if (!bowlerMap[bo.bowlerId]) bowlerMap[bo.bowlerId] = bo.overNumber;
            });
            const sorted = Object.entries(bowlerMap)
              .sort((a, b) => a[1] - b[1])
              .map(([id]) => id);
            if (format === 'FC') {
              // FC: keep all unique bowlers (no 5-slot limit)
              setSelectedBowlers(sorted.map(id => id));
              setBowlerAggression(sorted.map(() => 'N'));
            } else {
              const newBowlers = Array(5).fill(null);
              sorted.forEach((id, i) => { if (i < 5) newBowlers[i] = id; });
              setSelectedBowlers(newBowlers);
            }

            // If saved plan is a template, back up the custom state as blank
            if (sl.bowlingPlan && sl.bowlingPlan !== 'CUSTOM') {
              customBowlingBackup.current = Array.from({ length: totalOvers }, () => ({ bowlerId: null, aggression: 'N' }));
            }
          }
        }
      } catch {
        toast.error('Failed to load lineup data');
      } finally {
        setLoading(false);
      }
    })();
  }, [fixtureId]);

  /* ─── Derived data ─── */
  const selectedIds = useMemo(() => new Set(playing11.filter(Boolean)), [playing11]);

  const availablePlayers = useMemo(() => {
    return squad.filter((p) => !selectedIds.has(p.id));
  }, [squad, selectedIds]);

  const playing11Players = useMemo(() => {
    return playing11.map((id) => (id ? squad.find((p) => p.id === id) : null));
  }, [playing11, squad]);

  // Bowler candidates = playing 11 players who can bowl
  const bowlerCandidates = useMemo(() => {
    return playing11
      .filter(Boolean)
      .map((id) => squad.find((p) => p.id === id))
      .filter((p) => p && p.id !== keeperId && (p.role === 'BOWLER' || p.role === 'ALL_ROUNDER' || p.bowlType));
  }, [playing11, squad, keeperId]);

  const activeBowlerIds = useMemo(() => {
    const ids = new Set(selectedBowlers.filter(Boolean));
    customBowling.forEach((bo) => { if (bo?.bowlerId) ids.add(bo.bowlerId); });
    return ids;
  }, [selectedBowlers, customBowling]);

  const keeperOptions = useMemo(() => {
    return playing11Players.filter((p) => p && !activeBowlerIds.has(p.id));
  }, [playing11Players, activeBowlerIds]);

  const format = matchInfo?.format;
  const totalOvers = format === 'T20' ? 20 : format === 'ODI' ? 50 : 100;
  const maxPerBowler = format === 'T20' ? 4 : format === 'ODI' ? 10 : 30;

  // Auto-apply template when selected bowlers change (and a template plan is active)
  useEffect(() => {
    if (!format || format === 'FC' || bowlingPlan === 'CUSTOM') return;
    if (selectedBowlers.filter(Boolean).length < 5) return;
    const templates = format === 'T20' ? T20_TEMPLATES : ODI_TEMPLATES;
    const tpl = templates[bowlingPlan];
    if (!tpl) return;
    const newCb = tpl.map((bn) => ({
      bowlerId: selectedBowlers[bn - 1] || null,
      aggression: 'N',
    }));
    setCustomBowling(newCb);
  }, [selectedBowlers, bowlingPlan, format]);

  useEffect(() => {
    if (!keeperId) return;
    let changed = false;
    const nextSelected = selectedBowlers.map((id) => {
      if (id === keeperId) {
        changed = true;
        return null;
      }
      return id;
    });
    if (changed) setSelectedBowlers(nextSelected);

    const keeperUsedInOvers = customBowling.some((bo) => bo.bowlerId === keeperId);
    if (keeperUsedInOvers) {
      setCustomBowling((prev) => prev.map((bo) => (bo.bowlerId === keeperId ? { bowlerId: null, aggression: 'N' } : bo)));
      toast.error('Wicket-keeper removed from bowling orders');
    }
  }, [keeperId]);

  /* ─── Handlers ─── */

  const setPlayerAtPosition = (idx, playerId) => {
    const newPlaying = [...playing11];
    // If this player is already placed somewhere else, remove them
    const existingIdx = newPlaying.indexOf(playerId);
    if (existingIdx !== -1 && existingIdx !== idx) {
      newPlaying[existingIdx] = null;
    }
    newPlaying[idx] = playerId;
    setPlaying11(newPlaying);

    // Clear captain/keeper if removed
    if (captainId && !newPlaying.includes(captainId)) setCaptainId(null);
    if (keeperId && !newPlaying.includes(keeperId)) setKeeperId(null);
  };

  const removeFromPosition = (idx) => {
    const removedId = playing11[idx];
    const newPlaying = [...playing11];
    newPlaying[idx] = null;
    setPlaying11(newPlaying);

    const newAgg = [...batAggression];
    newAgg[idx] = 'N';
    setBatAggression(newAgg);

    if (captainId === removedId) setCaptainId(null);
    if (keeperId === removedId) setKeeperId(null);
  };

  const movePlayer = (idx, direction) => {
    const targetIdx = idx + direction;
    if (targetIdx < 0 || targetIdx > 10) return;
    const newPlaying = [...playing11];
    const newAgg = [...batAggression];
    [newPlaying[idx], newPlaying[targetIdx]] = [newPlaying[targetIdx], newPlaying[idx]];
    [newAgg[idx], newAgg[targetIdx]] = [newAgg[targetIdx], newAgg[idx]];
    setPlaying11(newPlaying);
    setBatAggression(newAgg);
  };

  const canAssignBowlerAtOver = (grid, overIndex, bowlerId) => {
    if (!bowlerId) return true;
    const prev = overIndex > 0 ? grid[overIndex - 1]?.bowlerId : null;
    const next = overIndex < grid.length - 1 ? grid[overIndex + 1]?.bowlerId : null;
    return bowlerId !== prev && bowlerId !== next;
  };

  const getPlayerFullName = (playerId) => {
    const player = squad.find((p) => p.id === playerId);
    return player ? `${player.firstName} ${player.lastName}` : 'Selected bowler';
  };

  const handleSave = async () => {
    const filledCount = playing11.filter(Boolean).length;
    if (filledCount < 11) {
      toast.error('Select all 11 players');
      return;
    }
    if (!captainId) { toast.error('Select a captain'); return; }
    if (!keeperId) { toast.error('Select a keeper'); return; }

    if (!matchInfo.isHome && !tossChoice) {
      toast.error('Choose heads or tails for the toss');
      return;
    }

    // Build bowling orders — use template when applicable, otherwise from customBowling
    const bowlingOrders = [];
    if (bowlingPlan !== 'CUSTOM' && format !== 'FC' && selectedBowlers.filter(Boolean).length === 5) {
      // Generate from template + selected bowlers
      const templates = format === 'T20' ? T20_TEMPLATES : ODI_TEMPLATES;
      const tpl = templates[bowlingPlan];
      if (tpl) {
        tpl.forEach((bn, i) => {
          const bid = selectedBowlers[bn - 1];
          if (bid) bowlingOrders.push({ overNumber: i + 1, bowlerId: bid, aggression: 'N' });
        });
      }
    } else {
      customBowling.forEach((bo, i) => {
        if (bo.bowlerId) {
          bowlingOrders.push({ overNumber: i + 1, bowlerId: bo.bowlerId, aggression: bo.aggression });
        }
      });
    }

    const payload = {
      captainId,
      keeperId,
      tossChoice: matchInfo.isHome ? null : tossChoice,
      batOrBowl,
      bowlingPlan,
      saveAsDefault,
      players: playing11.map((id, i) => ({
        playerId: id,
        battingPosition: i + 1,
        batAggression: batAggression[i],
      })),
      bowlingOrders,
    };

    setSaving(true);
    try {
      await saveLineupApi(fixtureId, payload);

      // Save FC strategy if FC format
      if (format === 'FC') {
        await saveFCStrategy(fixtureId, {
          declareInn1: fcDeclareInn1 ? parseInt(fcDeclareInn1, 10) : null,
          declareInn2Lead: fcDeclareInn2Lead ? parseInt(fcDeclareInn2Lead, 10) : null,
          followOn: fcFollowOn,
          declareInn3Lead: fcDeclareInn3Lead ? parseInt(fcDeclareInn3Lead, 10) : null,
        });
      }

      toast.success('Lineup saved!');
    } catch {
      toast.error('Failed to save lineup');
    } finally {
      setSaving(false);
    }
  };

  /* ─── Sort handler ─── */
  const handleSort = (key) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  };

  const sortedSquad = useMemo(() => {
    const list = [...squad];
    list.sort((a, b) => {
      let av = a[sortKey], bv = b[sortKey];
      if (typeof av === 'string') {
        av = av.toLowerCase(); bv = (bv || '').toLowerCase();
        return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
      }
      return sortDir === 'asc' ? (av || 0) - (bv || 0) : (bv || 0) - (av || 0);
    });
    return list;
  }, [squad, sortKey, sortDir]);

  /* ─── Custom bowling validation ─── */
  const bowlerOverCounts = useMemo(() => {
    const counts = {};
    customBowling.forEach((bo) => {
      if (bo.bowlerId) counts[bo.bowlerId] = (counts[bo.bowlerId] || 0) + 1;
    });
    return counts;
  }, [customBowling]);

  if (loading) return <div className="lu-page"><div className="lu-loading">Loading lineup data...</div></div>;
  if (!matchInfo) return <div className="lu-page"><div className="lu-loading">Match not found</div></div>;

  const fmtColor = FORMAT_COLORS[format] || '#94a3b8';

  return (
    <div className="lu-page">

      {/* ═══ Section 1: Match Info ═══ */}
      <div className="lu-match-info">
        <div className="lu-mi-teams">
          {matchInfo.homeTeamPicUrl ? (
            <img className="lu-mi-logo" src={`/api/files/${matchInfo.homeTeamPicUrl}`} alt="" />
          ) : (
            <span className="lu-mi-initials">{matchInfo.homeTeamName?.slice(0, 2).toUpperCase()}</span>
          )}
          <span className="lu-mi-team-name">{matchInfo.homeTeamName}</span>
          <span className="lu-mi-vs">vs</span>
          <span className="lu-mi-team-name">{matchInfo.awayTeamName}</span>
          {matchInfo.awayTeamPicUrl ? (
            <img className="lu-mi-logo" src={`/api/files/${matchInfo.awayTeamPicUrl}`} alt="" />
          ) : (
            <span className="lu-mi-initials">{matchInfo.awayTeamName?.slice(0, 2).toUpperCase()}</span>
          )}
        </div>
        <div className="lu-mi-details">
          <span className="lu-mi-tag" style={{ background: fmtColor + '18', color: fmtColor, borderColor: fmtColor + '40' }}>
            {format}
          </span>
          <span
            className="lu-mi-league-link"
            onClick={() => navigate(`/league/${matchInfo.leagueId}`)}
          >
            {matchInfo.leagueLabel}
          </span>
          <span className="lu-mi-detail">
            <HiOutlineCalendarDays /> {matchInfo.matchDate} · R{matchInfo.round}
          </span>
          <span className="lu-mi-detail">
            <HiOutlineMapPin /> {matchInfo.groundName || 'Home Ground'} · {matchInfo.pitchType}
          </span>
          {matchInfo.weather?.available ? (
            <span className="lu-mi-weather">
              <span className="lu-mi-weather-icon">{matchInfo.weather.icon}</span>
              {matchInfo.weather.condition} · {matchInfo.weather.temperature}°C · {matchInfo.weather.humidity}% humidity
            </span>
          ) : (
            <span className="lu-mi-weather lu-mi-weather-na">Forecast not yet available</span>
          )}
        </div>
      </div>

      {/* ═══ Section 2: Batting Lineup ═══ */}
      <div className="lu-section">
        <h2 className="lu-section-title">
          <HiOutlineUserGroup /> Batting Lineup
        </h2>
        <div className="lu-batting-grid">
          <div className="lu-batting-head">
            <span className="lu-bh-pos">#</span>
            <span className="lu-bh-player">Player</span>
            <span className="lu-bh-role">Role</span>
            <span className="lu-bh-agg">Aggression</span>
            <span className="lu-bh-tags">Tags</span>
            <span className="lu-bh-actions"></span>
          </div>
          {playing11.map((playerId, idx) => {
            const player = playerId ? squad.find((p) => p.id === playerId) : null;
            return (
              <div key={idx} className={`lu-batting-row ${player ? '' : 'lu-empty-slot'}`}>
                <span className="lu-br-pos">{idx + 1}</span>
                <div className="lu-br-player">
                  {player ? (
                    <span className="lu-br-name">{player.firstName} {player.lastName}</span>
                  ) : (
                    <select
                      className="lu-player-select"
                      value=""
                      onChange={(e) => setPlayerAtPosition(idx, e.target.value)}
                    >
                      <option value="">— Select Player —</option>
                      {availablePlayers.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.firstName} {p.lastName} ({ROLE_SHORT[p.role]})
                        </option>
                      ))}
                    </select>
                  )}
                </div>
                <span className="lu-br-role">
                  {player && <span className={`lu-role-badge ${player.role.toLowerCase()}`}>{ROLE_SHORT[player.role]}</span>}
                </span>
                <div className="lu-br-agg">
                  {player && (
                    <div className="lu-bat-agg-btns">
                      {['D', 'N', 'A'].map((v) => (
                        <button
                          key={v}
                          className={`lu-co-agg-btn ${batAggression[idx] === v ? (v === 'D' ? 'active-d' : v === 'A' ? 'active-a' : 'active') : ''}`}
                          onClick={() => {
                            const newAgg = [...batAggression];
                            newAgg[idx] = v;
                            setBatAggression(newAgg);
                          }}
                        >
                          {v}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <div className="lu-br-tags">
                  {player && captainId === playerId && <span className="lu-tag lu-tag-captain">C</span>}
                  {player && keeperId === playerId && <span className="lu-tag lu-tag-keeper">WK</span>}
                </div>
                <div className="lu-br-actions">
                  {player && (
                    <>
                      <button className="lu-move-btn" onClick={() => movePlayer(idx, -1)} disabled={idx === 0}>
                        <HiOutlineChevronUp />
                      </button>
                      <button className="lu-move-btn" onClick={() => movePlayer(idx, 1)} disabled={idx === 10}>
                        <HiOutlineChevronDown />
                      </button>
                      <button className="lu-remove-btn" onClick={() => removeFromPosition(idx)}>✕</button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Captain & Keeper */}
        <div className="lu-ck-row">
          <div className="lu-ck-item">
            <label>Captain</label>
            <select value={captainId || ''} onChange={(e) => setCaptainId(e.target.value || null)}>
              <option value="">— Select —</option>
              {playing11Players.filter(Boolean).map((p) => (
                <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>
              ))}
            </select>
          </div>
          <div className="lu-ck-item">
            <label>Wicket-Keeper</label>
            <select value={keeperId || ''} onChange={(e) => setKeeperId(e.target.value || null)}>
              <option value="">— Select —</option>
              {keeperOptions.map((p) => (
                <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* ═══ Section 3: Toss ═══ */}
      <div className="lu-section">
        <h2 className="lu-section-title">
          <HiOutlineTrophy /> Toss
        </h2>
        <div className="lu-toss-row">
          {!matchInfo.isHome && (
            <div className="lu-toss-group">
              <label>Call</label>
              <div className="lu-toss-buttons">
                <button className={`lu-toss-btn ${tossChoice === 'HEADS' ? 'active' : ''}`} onClick={() => setTossChoice('HEADS')}>Heads</button>
                <button className={`lu-toss-btn ${tossChoice === 'TAILS' ? 'active' : ''}`} onClick={() => setTossChoice('TAILS')}>Tails</button>
              </div>
            </div>
          )}
          <div className="lu-toss-group">
            <label>If you win the toss</label>
            <div className="lu-toss-buttons">
              <button className={`lu-toss-btn ${batOrBowl === 'BAT' ? 'active' : ''}`} onClick={() => setBatOrBowl('BAT')}>Bat First</button>
              <button className={`lu-toss-btn ${batOrBowl === 'BOWL' ? 'active' : ''}`} onClick={() => setBatOrBowl('BOWL')}>Bowl First</button>
            </div>
          </div>
        </div>
      </div>

      {/* ═══ Section 3.5: FC Strategy (FC only) ═══ */}
      {format === 'FC' && (
        <div className="lu-section">
          <h2 className="lu-section-title">FC Match Strategy</h2>
          <p className="lu-template-note">
            Set declaration targets and follow-on preference. You can update these between Day 1 and Day 2.
          </p>
          <div className="lu-fc-strategy">
            <div className="lu-fc-strat-row">
              <label className="lu-fc-strat-label">1st Innings Declaration</label>
              <div className="lu-fc-strat-input">
                <input
                  type="number"
                  min="0"
                  placeholder="Score total (e.g. 350)"
                  value={fcDeclareInn1}
                  onChange={e => setFcDeclareInn1(e.target.value)}
                />
                <span className="lu-fc-strat-hint">Used only if your team bats in innings 1. Leave empty for no declaration.</span>
              </div>
            </div>
            <div className="lu-fc-strat-row">
              <label className="lu-fc-strat-label">2nd Innings Declaration (Lead)</label>
              <div className="lu-fc-strat-input">
                <input
                  type="number"
                  min="0"
                  placeholder="Lead runs (e.g. 150)"
                  value={fcDeclareInn2Lead}
                  onChange={e => setFcDeclareInn2Lead(e.target.value)}
                />
                <span className="lu-fc-strat-hint">Used only if your team bats in innings 2. Leave empty for no declaration.</span>
              </div>
            </div>
            <div className="lu-fc-strat-row">
              <label className="lu-fc-strat-label">Follow-on (if leading by 200+)</label>
              <div className="lu-fc-strat-input">
                <div className="lu-fc-strat-toggle">
                  <button
                    className={`lu-fc-strat-btn ${fcFollowOn ? 'active' : ''}`}
                    onClick={() => setFcFollowOn(true)}
                  >Yes — Enforce</button>
                  <button
                    className={`lu-fc-strat-btn ${!fcFollowOn ? 'active' : ''}`}
                    onClick={() => setFcFollowOn(false)}
                  >No — Bat Again</button>
                </div>
                <span className="lu-fc-strat-hint">If your team leads by 200+, enforce follow-on or bat again.</span>
              </div>
            </div>
            <div className="lu-fc-strat-row">
              <label className="lu-fc-strat-label">3rd Innings Declaration (Lead)</label>
              <div className="lu-fc-strat-input">
                <input
                  type="number"
                  min="0"
                  placeholder="Lead runs (e.g. 250)"
                  value={fcDeclareInn3Lead}
                  onChange={e => setFcDeclareInn3Lead(e.target.value)}
                />
                <span className="lu-fc-strat-hint">Used only if your team bats in innings 3 (can depend on follow-on choice). Leave empty for no declaration.</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ═══ Section 4: Bowling Plan ═══ */}
      <div className="lu-section">
        <h2 className="lu-section-title">Bowling Plan</h2>

        {format === 'FC' ? (
          /* ── FC: Custom 100-over grid ── */
          <div className="lu-fc-bowling">
            <p className="lu-template-note">
              Select bowlers, click one to activate, then click grid cells to assign overs.
              Click assigned cells to cycle aggression (N→A→D). This 100-over plan repeats for the full match.
              Max {maxPerBowler} per bowler. No consecutive overs.
            </p>

            {/* Bowler rows — dynamic count */}
            <div className="lu-fc-panel">
              {selectedBowlers.map((bid, i) => {
                const count = bid ? customBowling.filter(bo => bo.bowlerId === bid).length : 0;
                const color = BOWLER_COLORS[i % BOWLER_COLORS.length];
                return (
                  <div
                    key={i}
                    className={`lu-fc-row ${activeBrush === i ? 'lu-fc-active' : ''}`}
                    style={{ borderColor: activeBrush === i ? color : undefined }}
                    onClick={() => setActiveBrush(i)}
                  >
                    <span className="lu-fc-dot" style={{ background: color }} />
                    <span className="lu-fc-label">B{i + 1}</span>
                    <select
                      className="lu-fc-select"
                      value={bid || ''}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => {
                        const newBowlers = [...selectedBowlers];
                        const oldBid = newBowlers[i];
                        if (oldBid && oldBid !== e.target.value) {
                          setCustomBowling(prev => prev.map(bo =>
                            bo.bowlerId === oldBid ? { bowlerId: null, aggression: 'N' } : bo
                          ));
                        }
                        newBowlers[i] = e.target.value || null;
                        setSelectedBowlers(newBowlers);
                      }}
                    >
                      <option value="">— Select Bowler —</option>
                      {bowlerCandidates
                        .filter((p) => p.id === bid || !selectedBowlers.includes(p.id))
                        .map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.firstName} {p.lastName} ({BOWL_TYPE_LABELS[p.bowlType] || 'Part-time'})
                          </option>
                        ))}
                    </select>
                    <span className="lu-fc-cnt" style={{ color }}>{count}/{maxPerBowler}</span>
                    {/* Remove bowler row button (only if not the last remaining) */}
                    {selectedBowlers.length > 1 && (
                      <button
                        className="lu-fc-remove-btn"
                        title="Remove bowler"
                        onClick={(e) => {
                          e.stopPropagation();
                          const oldBid = selectedBowlers[i];
                          if (oldBid) {
                            setCustomBowling(prev => prev.map(bo =>
                              bo.bowlerId === oldBid ? { bowlerId: null, aggression: 'N' } : bo
                            ));
                          }
                          const newBowlers = selectedBowlers.filter((_, idx) => idx !== i);
                          setSelectedBowlers(newBowlers);
                          const newAgg = bowlerAggression.filter((_, idx) => idx !== i);
                          setBowlerAggression(newAgg);
                          if (activeBrush >= newBowlers.length) setActiveBrush(newBowlers.length - 1);
                        }}
                      >
                        ×
                      </button>
                    )}
                  </div>
                );
              })}
              {/* Add bowler button */}
              {selectedBowlers.length < bowlerCandidates.length && (
                <button
                  className="lu-fc-add-btn"
                  onClick={() => {
                    setSelectedBowlers(prev => [...prev, null]);
                    setBowlerAggression(prev => [...prev, 'N']);
                  }}
                >
                  + Add Bowler
                </button>
              )}
              <div
                className={`lu-fc-row lu-fc-eraser ${activeBrush === -1 ? 'lu-fc-active' : ''}`}
                onClick={() => setActiveBrush(-1)}
              >
                <span className="lu-fc-dot" style={{ background: '#475569' }} />
                <span className="lu-fc-label">Eraser</span>
              </div>
            </div>

            {/* 10×10 over grid */}
            <div className="lu-fc-grid">
              {customBowling.map((bo, i) => {
                const slotIdx = bo.bowlerId ? selectedBowlers.indexOf(bo.bowlerId) : -1;
                const color = slotIdx >= 0 ? BOWLER_COLORS[slotIdx % BOWLER_COLORS.length] : null;
                const prevId = i > 0 ? customBowling[i - 1].bowlerId : null;
                const isConsecutive = bo.bowlerId && prevId && bo.bowlerId === prevId;
                return (
                  <div
                    key={i}
                    className={`lu-fc-cell ${isConsecutive ? 'lu-fc-err' : ''} ${color ? 'lu-fc-filled' : ''}`}
                    style={{
                      background: color ? color + '20' : undefined,
                      borderColor: isConsecutive ? '#ef4444' : color || undefined,
                    }}
                    onClick={() => {
                      const newCb = [...customBowling];
                      if (activeBrush === -1) {
                        // Eraser
                        newCb[i] = { bowlerId: null, aggression: 'N' };
                      } else if (newCb[i].bowlerId && newCb[i].bowlerId === selectedBowlers[activeBrush]) {
                        // Same bowler already here → cycle aggression: N→A→D→N
                        const cycle = { N: 'A', A: 'D', D: 'N' };
                        newCb[i] = { ...newCb[i], aggression: cycle[newCb[i].aggression] || 'N' };
                      } else {
                        // Assign active bowler
                        const bid = selectedBowlers[activeBrush];
                        if (!bid) { toast.error('Select a bowler for B' + (activeBrush + 1) + ' first'); return; }
                        if (!canAssignBowlerAtOver(newCb, i, bid)) {
                          toast.error(`${getPlayerFullName(bid)} cannot bowl consecutive overs`);
                          return;
                        }
                        if (newCb[i].bowlerId === bid) {
                          newCb[i] = { bowlerId: null, aggression: 'N' };
                        } else {
                          const cnt = newCb.filter(b => b.bowlerId === bid).length;
                          if (cnt >= maxPerBowler) {
                            toast.error(`${getPlayerFullName(bid)} has max ${maxPerBowler} overs`);
                            return;
                          }
                          newCb[i] = { bowlerId: bid, aggression: 'N' };
                        }
                      }
                      setCustomBowling(newCb);
                    }}
                  >
                    <span className="lu-fc-num">{i + 1}</span>
                    {slotIdx >= 0 && <span className="lu-fc-bwl" style={{ color }}>B{slotIdx + 1}</span>}
                    {bo.bowlerId && bo.aggression !== 'N' && (
                      <span className={`lu-fc-agg ${bo.aggression === 'A' ? 'lu-fc-agg-a' : 'lu-fc-agg-d'}`}>
                        {bo.aggression}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Summary */}
            <div className="lu-fc-summary">
              <span className="lu-fc-assigned">
                Assigned: {customBowling.filter(bo => bo.bowlerId).length} / 100
              </span>
              <span className="lu-fc-hint">
                Tip: Click an assigned cell again to cycle aggression (N→A→D)
              </span>
            </div>
          </div>
        ) : (
          <>
        {/* Template tabs */}
        <div className="lu-bowling-tabs">
          {['BALANCED', 'PACE_HEAVY', 'SPIN_HEAVY', 'CUSTOM'].map((plan) => (
            <button
              key={plan}
              className={`lu-bowl-tab ${bowlingPlan === plan ? 'active' : ''}`}
              onClick={() => {
                if (plan === 'CUSTOM') {
                  // Restore backed-up custom state (or keep current if already custom)
                  if (bowlingPlan !== 'CUSTOM' && customBowlingBackup.current) {
                    setCustomBowling(customBowlingBackup.current);
                  }
                  setBowlingPlan('CUSTOM');
                  return;
                }
                // Template click
                if (selectedBowlers.filter(Boolean).length < 5) {
                  toast.error('Select all 5 bowlers to apply a template');
                  return;
                }
                // Back up current custom state before overwriting
                if (bowlingPlan === 'CUSTOM') {
                  customBowlingBackup.current = [...customBowling.map((b) => ({ ...b }))];
                }
                const templates = format === 'T20' ? T20_TEMPLATES : ODI_TEMPLATES;
                const tpl = templates[plan];
                if (tpl) {
                  const newCb = tpl.map((bn) => ({
                    bowlerId: selectedBowlers[bn - 1] || null,
                    aggression: 'N',
                  }));
                  setCustomBowling(newCb);
                }
                setBowlingPlan(plan);
              }}
            >
              {TEMPLATE_LABELS[plan]}
            </button>
          ))}
        </div>

        {/* Bowler selection — hidden when Custom is active */}
        {bowlingPlan !== 'CUSTOM' && (
          <div className="lu-template-bowlers">
            <p className="lu-template-note">
              Select 5 bowlers from your playing XI to apply the template.
            </p>
            <div className="lu-bowler-slots">
              {[0, 1, 2, 3, 4].map((i) => (
                <div key={i} className="lu-bowler-slot">
                  <span className="lu-bowler-num">B{i + 1}</span>
                  <select
                    value={selectedBowlers[i] || ''}
                    onChange={(e) => {
                      const newBowlers = [...selectedBowlers];
                      newBowlers[i] = e.target.value || null;
                      setSelectedBowlers(newBowlers);
                    }}
                  >
                    <option value="">— Select Bowler —</option>
                    {bowlerCandidates
                      .filter((p) => p.id === selectedBowlers[i] || !selectedBowlers.includes(p.id))
                      .map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.firstName} {p.lastName} ({BOWL_TYPE_LABELS[p.bowlType] || p.bowlType || 'Part-time'})
                        </option>
                      ))}
                  </select>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Editable over grid — always shown */}
        <div className="lu-custom-bowling">
          <p className="lu-template-note">
            {bowlingPlan !== 'CUSTOM'
              ? `${TEMPLATE_LABELS[bowlingPlan]} template applied. Edit any over to switch to Custom.`
              : `Custom order · Max ${maxPerBowler} overs per bowler · No consecutive overs.`}
          </p>
          <div className="lu-co-grid">
            {customBowling.map((bo, i) => {
              const prevBowler = i > 0 ? customBowling[i - 1].bowlerId : null;
              return (
                <div key={i} className="lu-co-cell">
                  <span className="lu-co-num">{i + 1}</span>
                  <select
                    className="lu-co-bowler"
                    value={bo.bowlerId || ''}
                    onChange={(e) => {
                      const nextBowlerId = e.target.value || null;
                      const newCb = [...customBowling];
                      if (!canAssignBowlerAtOver(newCb, i, nextBowlerId)) {
                        toast.error(`${getPlayerFullName(nextBowlerId)} cannot bowl consecutive overs`);
                        return;
                      }
                      newCb[i] = { ...newCb[i], bowlerId: nextBowlerId };
                      setCustomBowling(newCb);
                      if (bowlingPlan !== 'CUSTOM') {
                        customBowlingBackup.current = null;
                        setBowlingPlan('CUSTOM');
                      }
                    }}
                  >
                    <option value="">—</option>
                    {bowlerCandidates.map((p) => {
                      const count = bowlerOverCounts[p.id] || 0;
                      const isAtMax = count >= maxPerBowler && p.id !== bo.bowlerId;
                      const nextBowler = i < customBowling.length - 1 ? customBowling[i + 1].bowlerId : null;
                      const isConsecutive = (p.id === prevBowler || p.id === nextBowler) && p.id !== bo.bowlerId;
                      return (
                        <option key={p.id} value={p.id} disabled={isAtMax || isConsecutive}>
                          {p.lastName} ({count}/{maxPerBowler})
                        </option>
                      );
                    })}
                  </select>
                  <div className="lu-co-agg">
                    {['D', 'N', 'A'].map((v) => (
                      <button
                        key={v}
                        className={`lu-co-agg-btn ${bo.aggression === v ? (v === 'D' ? 'active-d' : v === 'A' ? 'active-a' : 'active') : ''}`}
                        onClick={() => {
                          const newCb = [...customBowling];
                          newCb[i] = { ...newCb[i], aggression: v };
                          setCustomBowling(newCb);
                          if (bowlingPlan !== 'CUSTOM') {
                            customBowlingBackup.current = null;
                            setBowlingPlan('CUSTOM');
                          }
                        }}
                      >
                        {v}
                      </button>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
          </>
        )}
      </div>

      {/* ═══ Section 5: Squad Player Pool ═══ */}
      <div className="lu-section">
        <h2 className="lu-section-title">
          <HiOutlineUserGroup /> Squad Players
        </h2>
        <div className="lu-pool-table">
          <div className="lu-pool-head">
            <SortHeader k="firstName" label="Player" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="role" label="Role" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="batRating" label="BAT" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="bowlRating" label="BOWL" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="keeperRating" label="WK" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="fldRating" label="FLD" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="rating" label="OVR" current={sortKey} dir={sortDir} onSort={handleSort} />
            <SortHeader k="fitness" label="FIT" current={sortKey} dir={sortDir} onSort={handleSort} />
            <span className="lu-ph-col">Style</span>
            <span className="lu-ph-col">Status</span>
          </div>
          {sortedSquad.map((p) => {
            const isSelected = selectedIds.has(p.id);
            return (
              <div key={p.id} className={`lu-pool-row ${isSelected ? 'lu-pool-selected' : ''}`}>
                <span className="lu-pr-name">{p.firstName} {p.lastName}</span>
                <span className="lu-pr-role">
                  <span className={`lu-role-badge ${p.role.toLowerCase()}`}>{ROLE_SHORT[p.role]}</span>
                </span>
                <span className="lu-pr-stat">{Math.floor(p.batRating)}</span>
                <span className="lu-pr-stat">{Math.floor(p.bowlRating)}</span>
                <span className="lu-pr-stat">{Math.floor(p.keeperRating)}</span>
                <span className="lu-pr-stat">{Math.floor(p.fldRating)}</span>
                <span className="lu-pr-stat lu-pr-ovr">{p.rating}</span>
                <span className="lu-pr-stat">{p.fitness}</span>
                <span className="lu-pr-style">
                  {p.batHand} {p.bowlType ? `· ${BOWL_TYPE_LABELS[p.bowlType] || p.bowlType}` : ''}
                </span>
                <span className="lu-pr-status">
                  {isSelected ? <span className="lu-selected-badge">IN XI</span> : null}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* ═══ Save Button ═══ */}
      <div className="lu-save-bar">
        <label className="lu-default-check">
          <input
            type="checkbox"
            checked={saveAsDefault}
            onChange={(e) => setSaveAsDefault(e.target.checked)}
          />
          Save as default order
        </label>
        <button className="lu-save-btn" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Lineup'}
        </button>
      </div>
    </div>
  );
}

function SortHeader({ k, label, current, dir, onSort }) {
  const isActive = current === k;
  return (
    <span className={`lu-ph-col lu-sortable ${isActive ? 'lu-sort-active' : ''}`} onClick={() => onSort(k)}>
      {label}
      {isActive && (dir === 'asc' ? ' ▲' : ' ▼')}
    </span>
  );
}
