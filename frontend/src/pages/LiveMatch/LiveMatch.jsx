import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getCommentary, getMatchResult } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlinePlayCircle,
  HiOutlinePauseCircle,
  HiOutlineForward,
  HiOutlineTrophy,
  HiOutlineClipboardDocumentList,
  HiOutlineChevronLeft,
} from 'react-icons/hi2';
import './LiveMatch.css';

const BALL_INTERVAL = 5000;
const FAST_INTERVAL = 1000;
const STORAGE_KEY = (id) => `live_match_${id}`;

/* ─── helpers ─── */
function buildBatsmenMap(balls) {
  const map = {};
  balls.forEach((b) => {
    if (!map[b.batsman]) map[b.batsman] = { runs: 0, balls: 0 };
    map[b.batsman].runs += (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
    if (!b.isWide && !b.isNoBall) map[b.batsman].balls += 1;
    else if (b.isNoBall) map[b.batsman].balls += 1;
  });
  return map;
}
function buildBowlerMap(balls) {
  const map = {};
  balls.forEach((b) => {
    if (!map[b.bowler]) map[b.bowler] = { overs: 0, legalBalls: 0, runs: 0, wickets: 0, maidens: 0 };
    const bw = map[b.bowler];
    bw.runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
    if (b.isWicket && b.dismissalType !== 'RUN_OUT') bw.wickets += 1;
    if (!b.isWide && !b.isNoBall) bw.legalBalls += 1;
    bw.overs = Math.floor(bw.legalBalls / 6) + (bw.legalBalls % 6) / 10;
  });
  return map;
}
function oversDisplay(legalBalls) {
  return Math.floor(legalBalls / 6) + '.' + (legalBalls % 6);
}
function getLegalCount(balls) {
  return balls.filter((b) => !b.isWide && !b.isNoBall).length;
}

export default function LiveMatch() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const [commentary, setCommentary] = useState(null);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('commentary'); // 'commentary' | 'scorecard'

  // ─── Restore from sessionStorage synchronously ───
  const saved = useMemo(() => {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY(fixtureId));
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }, [fixtureId]);

  // Playback
  const [currentInnings, setCurrentInnings] = useState(saved?.inn ?? 0);
  const [currentBallIdx, setCurrentBallIdx] = useState(saved?.idx ?? 0);
  const [isPlaying, setIsPlaying] = useState(!saved?.ended && !saved?.brk);
  const [isFast, setIsFast] = useState(false);
  const [matchEnded, setMatchEnded] = useState(saved?.ended ?? false);
  const [inningsBreak, setInningsBreak] = useState(saved?.brk ?? false);
  const [scActiveInnings, setScActiveInnings] = useState(1);
  const timerRef = useRef(null);
  const feedRef = useRef(null);

  // ─── Save to sessionStorage on change ───
  useEffect(() => {
    try {
      sessionStorage.setItem(
        STORAGE_KEY(fixtureId),
        JSON.stringify({ inn: currentInnings, idx: currentBallIdx, ended: matchEnded, brk: inningsBreak })
      );
    } catch { /* ignore */ }
  }, [fixtureId, currentInnings, currentBallIdx, matchEnded, inningsBreak]);

  // ─── Load match data ───
  useEffect(() => {
    const loadData = async () => {
      try {
        const [commRes, resRes] = await Promise.all([
          getCommentary(fixtureId),
          getMatchResult(fixtureId),
        ]);
        if (commRes.data.found) setCommentary(commRes.data);
        if (resRes.data.found) setResult(resRes.data);
      } catch {
        toast.error('Failed to load match data');
      } finally {
        setLoading(false);
      }
    };
    loadData();
  }, [fixtureId]);

  const allBalls = commentary?.innings?.[currentInnings]?.ballEvents || [];
  const inn = commentary?.innings?.[currentInnings];
  const visibleBalls = allBalls.slice(0, currentBallIdx);

  // ─── Compute running score from visible balls ───
  const liveScore = useMemo(() => {
    let runs = 0, wkts = 0;
    visibleBalls.forEach((b) => { runs += b.runs; if (b.isWicket) wkts++; });
    const legal = getLegalCount(visibleBalls);
    return { runs, wkts, overs: oversDisplay(legal) };
  }, [visibleBalls]);

  // ─── Batsmen at crease & bowler from visible balls ───
  const { batsmenAtCrease, currentBowler, batsmenMap, bowlerMap } = useMemo(() => {
    const batMap = buildBatsmenMap(visibleBalls);
    const bowlMap = buildBowlerMap(visibleBalls);
    const dismissed = new Set();
    visibleBalls.forEach((b) => { if (b.isWicket) dismissed.add(b.batsman); });
    const atCrease = Object.keys(batMap).filter((n) => !dismissed.has(n));
    const lastBowler = visibleBalls.length > 0 ? visibleBalls[visibleBalls.length - 1].bowler : null;
    return { batsmenAtCrease: atCrease.slice(-2), currentBowler: lastBowler, batsmenMap: batMap, bowlerMap: bowlMap };
  }, [visibleBalls]);

  // ─── Build over-grouped feed with over summaries ───
  const feedItems = useMemo(() => {
    if (visibleBalls.length === 0) return [];
    const items = [];
    let prevOver = null;
    let overBalls = [];

    for (let i = 0; i < visibleBalls.length; i++) {
      const b = visibleBalls[i];
      const overNum = b.over;

      if (prevOver !== null && overNum !== prevOver) {
        // Insert over summary for completed over
        items.push({ type: 'overSummary', over: prevOver, balls: [...overBalls] });
        overBalls = [];
      }
      items.push({ type: 'ball', ball: b, index: i });
      overBalls.push(b);
      prevOver = overNum;
    }
    return items;
  }, [visibleBalls]);

  // ─── Advance ball ───
  const advanceBall = useCallback(() => {
    if (inningsBreak) return;
    setCurrentBallIdx((prev) => {
      const nextIdx = prev + 1;
      if (nextIdx > allBalls.length) {
        if (currentInnings === 0 && commentary?.innings?.length > 1) {
          setIsPlaying(false);
          setInningsBreak(true);
          return prev;
        } else {
          setIsPlaying(false);
          setMatchEnded(true);
          return prev;
        }
      }
      return nextIdx;
    });
  }, [allBalls.length, currentInnings, commentary, inningsBreak]);

  const startSecondInnings = () => {
    setCurrentInnings(1);
    setCurrentBallIdx(0);
    setInningsBreak(false);
    setIsPlaying(true);
  };

  // ─── Timer ───
  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    if (isPlaying && !matchEnded && !inningsBreak && allBalls.length > 0) {
      timerRef.current = setInterval(advanceBall, isFast ? FAST_INTERVAL : BALL_INTERVAL);
    }
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [isPlaying, isFast, matchEnded, inningsBreak, advanceBall, allBalls.length]);

  // ─── Auto-scroll feed ───
  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [currentBallIdx, activeTab]);

  // Scorecard data — progressive during live, full after match ends
  const liveScorecard = useMemo(() => {
    if (!commentary) return [];
    const innings = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (!matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (matchEnded) {
        balls = innData.ballEvents || [];
      } else if (i < currentInnings) {
        balls = innData.ballEvents || [];
      } else {
        balls = (innData.ballEvents || []).slice(0, currentBallIdx);
      }

      const batMap = {};
      const batOrder = [];
      const dismissed = new Set();
      let extras = 0;
      balls.forEach((b) => {
        if (!batMap[b.batsman]) { batMap[b.batsman] = { playerName: b.batsman, runs: 0, balls: 0, fours: 0, sixes: 0, dismissal: null, bowler: null, fielder: null, notOut: true }; batOrder.push(b.batsman); }
        const bm = batMap[b.batsman];
        bm.runs += (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
        if (!b.isWide && !b.isNoBall) bm.balls += 1;
        else if (b.isNoBall) bm.balls += 1;
        if (b.isBoundary && !b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) bm.fours += 1;
        if (b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) bm.sixes += 1;
        if (b.isWicket) { dismissed.add(b.batsman); bm.notOut = false; bm.dismissal = b.dismissalType || 'out'; bm.bowler = b.bowler; bm.fielder = b.fielder || null; }
        if (b.isWide || b.isNoBall || b.isBye || b.isLegBye) extras += b.runs;
      });
      const battingCard = batOrder.map((name) => {
        const bm = batMap[name];
        return { ...bm, strikeRate: bm.balls > 0 ? (bm.runs / bm.balls) * 100 : 0 };
      });

      const bowlMap = {};
      const bowlOrder = [];
      balls.forEach((b) => {
        if (!bowlMap[b.bowler]) { bowlMap[b.bowler] = { playerName: b.bowler, legalBalls: 0, maidens: 0, runs: 0, wickets: 0 }; bowlOrder.push(b.bowler); }
        const bw = bowlMap[b.bowler];
        bw.runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
      if (b.isWicket && b.dismissalType !== 'RUN_OUT') bw.wickets += 1;
        if (!b.isWide && !b.isNoBall) bw.legalBalls += 1;
      });
      const bowlerOverRuns = {};
      balls.forEach((b) => {
        const key = `${b.bowler}_${b.over}`;
        if (!bowlerOverRuns[key]) bowlerOverRuns[key] = { bowler: b.bowler, runs: 0, legalBalls: 0 };
        bowlerOverRuns[key].runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
        if (!b.isWide && !b.isNoBall) bowlerOverRuns[key].legalBalls += 1;
      });
      Object.values(bowlerOverRuns).forEach((ov) => {
        if (ov.legalBalls === 6 && ov.runs === 0 && bowlMap[ov.bowler]) bowlMap[ov.bowler].maidens += 1;
      });
      const bowlingCard = bowlOrder.map((name) => {
        const bw = bowlMap[name];
        const overs = Math.floor(bw.legalBalls / 6) + '.' + (bw.legalBalls % 6);
        const oversNum = bw.legalBalls / 6;
        return { ...bw, overs, economy: oversNum > 0 ? bw.runs / oversNum : 0 };
      });

      let totalRuns = 0, totalWkts = 0;
      balls.forEach((b) => { totalRuns += b.runs; if (b.isWicket) totalWkts++; });
      const legal = getLegalCount(balls);
      const scoreDisplay = `${totalRuns}/${totalWkts} (${oversDisplay(legal)} ov)`;

      innings.push({
        inningsNumber: i + 1,
        battingTeam: innData.battingTeam,
        scoreDisplay,
        battingCard,
        bowlingCard,
        extras,
      });
    }
    return innings;
  }, [commentary, currentInnings, currentBallIdx, matchEnded]);

  const scInn = liveScorecard.find((i) => i.inningsNumber === scActiveInnings);

  if (loading) return <div className="lm-loading">Loading match...</div>;
  if (!commentary || !result) return <div className="lm-loading">Match data not available.</div>;

  const battingTeam = inn?.battingTeam || '—';
  const firstInnScore = commentary.innings[0]?.scoreDisplay;
  const target = currentInnings === 1 ? parseInt(firstInnScore) + 1 : null;
  const need = target ? Math.max(0, target - liveScore.runs) : null;

  // Last completed over for recent balls display
  const lastOverBalls = (() => {
    const curOver = visibleBalls.length > 0 ? visibleBalls[visibleBalls.length - 1].over : -1;
    return visibleBalls.filter((b) => b.over === curOver);
  })();

  return (
    <div className="lm-page">
      {/* ─── Sticky Score Bar ─── */}
      <div className="lm-score-bar">
        <button className="lm-back" onClick={() => navigate('/challenges')}>
          <HiOutlineChevronLeft />
        </button>
        <div className="lm-score-info">
          <span className="lm-team-name">{battingTeam}</span>
          <span className="lm-score-num">{liveScore.runs}/{liveScore.wkts}</span>
          <span className="lm-overs-num">({liveScore.overs} ov)</span>
          {target && <span className="lm-need">Need {need} runs</span>}
        </div>
        <div className="lm-bar-controls">
          <button className={`lm-bar-btn ${isPlaying ? 'active' : ''}`} onClick={() => setIsPlaying((p) => !p)} disabled={matchEnded || inningsBreak}>
            {isPlaying ? <HiOutlinePauseCircle /> : <HiOutlinePlayCircle />}
          </button>
          <button className={`lm-bar-btn ${isFast ? 'active' : ''}`} onClick={() => setIsFast((f) => !f)} disabled={matchEnded || inningsBreak}>
            <HiOutlineForward />
          </button>
        </div>
      </div>

      {/* ─── Tabs ─── */}
      <div className="lm-tabs">
        <button className={`lm-tab ${activeTab === 'commentary' ? 'active' : ''}`} onClick={() => setActiveTab('commentary')}>
          Commentary
        </button>
        <button className={`lm-tab ${activeTab === 'scorecard' ? 'active' : ''}`} onClick={() => setActiveTab('scorecard')}>
          Scorecard
        </button>
      </div>

      {/* ─── Commentary Tab ─── */}
      {activeTab === 'commentary' && (
        <div className="lm-commentary">
          {/* Current batsmen & bowler strip */}
          <div className="lm-strip">
            <div className="lm-strip-batsmen">
              {batsmenAtCrease.map((name) => {
                const stats = batsmenMap[name] || { runs: 0, balls: 0 };
                const isStriker = visibleBalls.length > 0 && visibleBalls[visibleBalls.length - 1].batsman === name;
                return (
                  <span key={name} className={`lm-strip-bat ${isStriker ? 'striker' : ''}`}>
                    {name} {stats.runs}({stats.balls}){isStriker ? '*' : ''}
                  </span>
                );
              })}
            </div>
            {currentBowler && bowlerMap[currentBowler] && (
              <div className="lm-strip-bowler">
                {currentBowler} {bowlerMap[currentBowler].wickets}-{bowlerMap[currentBowler].runs}
              </div>
            )}
          </div>

          {/* This over chips */}
          <div className="lm-this-over">
            <span className="lm-over-label">This over:</span>
            {lastOverBalls.map((b, i) => (
              <span key={i} className={`lm-chip ${chipClass(b)}`}>
                {chipText(b)}
              </span>
            ))}
          </div>

          {/* Feed */}
          <div className="lm-feed" ref={feedRef}>
            {visibleBalls.length === 0 ? (
              <div className="lm-feed-empty">Match starting...</div>
            ) : (
              [...feedItems].reverse().map((item, idx) => {
                if (item.type === 'overSummary') {
                  return <OverSummary key={`os-${item.over}`} over={item.over} balls={item.balls}
                    teamName={battingTeam} allBallsSoFar={visibleBalls} />;
                }
                const b = item.ball;
                return (
                  <div key={`b-${item.index}`} className={`lm-ball-row ${item.index === visibleBalls.length - 1 ? 'lm-latest' : ''} ${ballRowClass(b)}`}>
                    <span className="lm-ball-over">{b.overBall}</span>
                    <span className={`lm-ball-result ${ballResultClass(b)}`}>{ballResultText(b)}</span>
                    <div className="lm-ball-detail">
                      <span className="lm-ball-players">{b.bowler} to {b.batsman}</span>
                      {b.commentary && <span className="lm-ball-text">{b.commentary}</span>}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* ─── Scorecard Tab ─── */}
      {activeTab === 'scorecard' && (
        <div className="lm-scorecard">
          <div className="lm-sc-tabs">
            {liveScorecard.map((rInn) => (
              <button key={rInn.inningsNumber}
                className={`lm-sc-tab ${scActiveInnings === rInn.inningsNumber ? 'active' : ''}`}
                onClick={() => setScActiveInnings(rInn.inningsNumber)}>
                <span>{rInn.battingTeam}</span>
                <span className="lm-sc-tab-score">{rInn.scoreDisplay}</span>
              </button>
            ))}
          </div>
          {scInn && (
            <>
              <div className="lm-sc-section">
                <h3>Batting</h3>
                <div className="lm-sc-table">
                  <div className="lm-sc-head">
                    <span className="lm-sc-name">Batter</span>
                    <span className="lm-sc-num">R</span>
                    <span className="lm-sc-num">B</span>
                    <span className="lm-sc-num">4s</span>
                    <span className="lm-sc-num">6s</span>
                    <span className="lm-sc-num">SR</span>
                  </div>
                  {scInn.battingCard?.map((bc, i) => (
                    <div key={i} className={`lm-sc-row ${bc.notOut ? 'lm-sc-notout' : ''}`}>
                      <span className="lm-sc-name">
                        {bc.playerName}{bc.notOut ? '*' : ''}
                        <span className="lm-sc-dismissal">
                          {bc.notOut ? 'not out' : formatDismissal(bc)}
                        </span>
                      </span>
                      <span className={`lm-sc-num ${bc.runs >= 50 ? 'lm-sc-milestone' : ''}`}>{bc.runs}</span>
                      <span className="lm-sc-num">{bc.balls}</span>
                      <span className="lm-sc-num">{bc.fours}</span>
                      <span className="lm-sc-num">{bc.sixes}</span>
                      <span className="lm-sc-num">{bc.strikeRate?.toFixed(1)}</span>
                    </div>
                  ))}
                  <div className="lm-sc-footer">
                    <span>Extras: {scInn.extras}</span>
                    <span>Total: {scInn.scoreDisplay}</span>
                  </div>
                </div>
              </div>
              <div className="lm-sc-section">
                <h3>Bowling</h3>
                <div className="lm-sc-table">
                  <div className="lm-sc-head">
                    <span className="lm-sc-name">Bowler</span>
                    <span className="lm-sc-num">O</span>
                    <span className="lm-sc-num">M</span>
                    <span className="lm-sc-num">R</span>
                    <span className="lm-sc-num">W</span>
                    <span className="lm-sc-num">Econ</span>
                  </div>
                  {scInn.bowlingCard?.map((bc, i) => (
                    <div key={i} className={`lm-sc-row ${bc.wickets >= 3 ? 'lm-sc-haul' : ''}`}>
                      <span className="lm-sc-name">{bc.playerName}</span>
                      <span className="lm-sc-num">{bc.overs}</span>
                      <span className="lm-sc-num">{bc.maidens}</span>
                      <span className="lm-sc-num">{bc.runs}</span>
                      <span className={`lm-sc-num ${bc.wickets >= 3 ? 'lm-sc-milestone' : ''}`}>{bc.wickets}</span>
                      <span className="lm-sc-num">{bc.economy?.toFixed(1)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* ─── Innings Break Overlay ─── */}
      {inningsBreak && (
        <div className="lm-overlay">
          <div className="lm-overlay-card">
            <h2>Innings Break</h2>
            <p className="lm-overlay-score">{battingTeam}: {liveScore.runs}/{liveScore.wkts} ({liveScore.overs} ov)</p>
            <p className="lm-overlay-target">
              {commentary.innings[1]?.battingTeam} need {parseInt(firstInnScore) + 1} runs to win
            </p>
            <button className="lm-overlay-btn" onClick={startSecondInnings}>
              Start 2nd Innings
            </button>
          </div>
        </div>
      )}

      {/* ─── Match Ended Overlay ─── */}
      {matchEnded && (
        <div className="lm-overlay">
          <div className="lm-overlay-card">
            <HiOutlineTrophy className="lm-overlay-icon" />
            <h2>{result.summary}</h2>
            {result.manOfMatch && <p className="lm-overlay-motm">Player of the Match: {result.manOfMatch}</p>}
            <div className="lm-overlay-scores">
              {result.innings?.map((rInn, i) => (
                <span key={i} className="lm-overlay-score-item">{rInn.battingTeam}: {rInn.scoreDisplay}</span>
              ))}
            </div>
            <div className="lm-overlay-actions">
              <button onClick={() => { setMatchEnded(false); setActiveTab('scorecard'); }}>
                <HiOutlineTrophy /> View Scorecard
              </button>
              <button onClick={() => navigate(`/match/${fixtureId}/commentary`)}>
                <HiOutlineClipboardDocumentList /> Full Commentary
              </button>
              <button className="lm-overlay-back" onClick={() => navigate('/challenges')}>
                Back to Challenges
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ─── Over Summary Block ─── */
function OverSummary({ over, balls, teamName, allBallsSoFar }) {
  // Sum up to end of this over
  const ballsUpToHere = allBallsSoFar.filter((b) => b.over <= over);
  let totalRuns = 0, totalWkts = 0;
  ballsUpToHere.forEach((b) => { totalRuns += b.runs; if (b.isWicket) totalWkts++; });
  const legal = getLegalCount(ballsUpToHere);
  const ovDisp = oversDisplay(legal);

  // Batsmen at crease at end of this over
  const batMap = buildBatsmenMap(ballsUpToHere);
  const dismissed = new Set();
  ballsUpToHere.forEach((b) => { if (b.isWicket) dismissed.add(b.batsman); });
  const atCrease = Object.keys(batMap).filter((n) => !dismissed.has(n)).slice(-2);

  // Over runs
  let overRuns = 0;
  balls.forEach((b) => { overRuns += b.runs; });

  // Bowler for this over
  const bowler = balls[0]?.bowler;
  const bowlMap = buildBowlerMap(ballsUpToHere);
  const bowlStats = bowlMap[bowler];

  return (
    <div className="lm-over-summary">
      <div className="lm-os-header">
        <span className="lm-os-title">End of Over {over}</span>
        <span className="lm-os-over-runs">{overRuns} runs</span>
      </div>
      <div className="lm-os-score">
        {teamName}: <strong>{totalRuns}/{totalWkts}</strong> ({ovDisp} ov)
      </div>
      <div className="lm-os-chips">
        {balls.map((b, i) => (
          <span key={i} className={`lm-chip ${chipClass(b)}`}>{chipText(b)}</span>
        ))}
      </div>
      <div className="lm-os-players">
        {atCrease.map((name) => {
          const s = batMap[name] || { runs: 0, balls: 0 };
          return <span key={name} className="lm-os-bat">{name} {s.runs}({s.balls})</span>;
        })}
      </div>
      {bowler && bowlStats && (
        <div className="lm-os-bowler">{bowler}: {bowlStats.wickets}-{bowlStats.runs}</div>
      )}
    </div>
  );
}

/* ─── Utility functions ─── */
function chipClass(b) {
  if (b.isWicket) return 'ch-wicket';
  if (b.isSix) return 'ch-six';
  if (b.isBoundary) return 'ch-four';
  if (b.isWide || b.isNoBall) return 'ch-extra';
  if (b.isBoundary && !b.isBye && !b.isLegBye && !b.isWide && !b.isNoBall) return 'ch-four';
  if (b.isWide || b.isNoBall || b.isBye || b.isLegBye) return 'ch-extra';
  if (b.runs === 0) return 'ch-dot';
  return 'ch-run';
}
function chipText(b) {
  if (b.isWicket) return 'W';
  if (b.isWide) return 'Wd';
  if (b.isNoBall) return 'Nb';
  if (b.isWide) return `${b.runs}wd`;
  if (b.isNoBall) return `${b.runs}nb`;
  if (b.isBye) return `${b.runs}b`;
  if (b.isLegBye) return `${b.runs}lb`;
  if (b.isSix) return '6';
  if (b.isBoundary) return '4';
  return b.runs;
}
function ballRowClass(b) {
  if (b.isWicket) return 'lm-row-wicket';
  if (b.isSix) return 'lm-row-six';
  if (b.isBoundary) return 'lm-row-four';
  if (b.isBoundary && !b.isBye && !b.isLegBye && !b.isWide && !b.isNoBall) return 'lm-row-four';
  return '';
}
function ballResultClass(b) {
  if (b.isWicket) return 'lmr-wicket';
  if (b.isSix) return 'lmr-six';
  if (b.isBoundary) return 'lmr-four';
  if (b.isBoundary && !b.isBye && !b.isLegBye && !b.isWide && !b.isNoBall) return 'lmr-four';
  if (b.runs === 0 && !b.isWide && !b.isNoBall) return 'lmr-dot';
  return '';
}
function ballResultText(b) {
  if (b.isWicket) return 'OUT';
  if (b.isWide) return `${b.runs}wd`;
  if (b.isNoBall) return `${b.runs}nb`;
  if (b.isBye) return `${b.runs}b`;
  if (b.isLegBye) return `${b.runs}lb`;
  if (b.isSix) return 'SIX';
  if (b.isBoundary) return 'FOUR';
  if (b.runs === 0) return '•';
  return `${b.runs} run${b.runs > 1 ? 's' : ''}`;
}
function formatDismissal(bc) {
  if (!bc.dismissal) return '';
  const d = bc.dismissal.toLowerCase();
  if (d === 'bowled') return `b ${bc.bowler}`;
  if (d === 'caught') return `c ${bc.fielder || ''} b ${bc.bowler}`;
  if (d === 'caught_behind') return `c †${bc.fielder || 'wk'} b ${bc.bowler}`;
  if (d === 'lbw') return `lbw b ${bc.bowler}`;
  if (d === 'stumped') return `st †${bc.fielder || 'wk'} b ${bc.bowler}`;
  if (d === 'run_out') return `run out (${bc.fielder || ''})`;
  if (d === 'hit_wicket') return `hit wicket b ${bc.bowler}`;
  return `${d} ${bc.bowler || ''}`;
}
