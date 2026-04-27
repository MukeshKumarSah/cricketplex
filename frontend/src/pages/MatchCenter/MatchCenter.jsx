import { useState, useEffect, useRef, useMemo, Fragment } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { getCommentary, getMatchResult, getRivalry } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineChevronLeft,
  HiOutlineMapPin,
  HiOutlineCalendarDays,
  HiOutlineClipboardDocumentList,
  HiOutlineUserGroup,
} from 'react-icons/hi2';
import './MatchCenter.css';
import { fileUrl } from '../../api/config';

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
    if (!map[b.bowler]) map[b.bowler] = { legalBalls: 0, runs: 0, wickets: 0, maidens: 0 };
    const bw = map[b.bowler];
    bw.runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
    if (b.isWicket && b.dismissalType !== 'RUN_OUT') bw.wickets += 1;
    if (!b.isWide && !b.isNoBall) bw.legalBalls += 1;
  });
  return map;
}
function oversDisplay(legalBalls) {
  return Math.floor(legalBalls / 6) + '.' + (legalBalls % 6);
}
function getLegalCount(balls) {
  return balls.filter((b) => !b.isWide && !b.isNoBall).length;
}

/* ─── phase ranges ─── */
function getPhases(format) {
  if (format === 'T20') return { powerplay: [0, 5], middle: [6, 15], death: [16, 19] };
  if (format === 'FC') return { early: [0, 9], consolidation: [10, 24], acceleration: [25, 39], late: [40, 49] };
  return { powerplay: [0, 9], middle: [10, 39], death: [40, 49] };
}

/* ─── Compute current match position from elapsed time (supports N innings + FC session breaks) ─── */
function computePosition(createdAt, ballCounts, ballInterval, breakDuration, sessionBreakSec, sessionBreakPositions) {
  if (!createdAt || !ballCounts || ballCounts.length === 0) {
    return { currentInnings: 0, currentBallIdx: 0, matchEnded: true, inningsBreak: false, sessionBreak: false, breakRemaining: 0 };
  }
  const startMs = typeof createdAt === 'number' ? createdAt : new Date(createdAt).getTime();
  if (isNaN(startMs)) {
    return { currentInnings: 0, currentBallIdx: 0, matchEnded: true, inningsBreak: false, sessionBreak: false, breakRemaining: 0 };
  }
  const elapsed = Math.max(0, (Date.now() - startMs) / 1000);

  let timeConsumed = 0;

  for (let i = 0; i < ballCounts.length; i++) {
    const innBalls = ballCounts[i] || 0;
    const breaks = (sessionBreakPositions && sessionBreakPositions[i]) || []; // sorted ball-event indices
    let ballsDone = 0;
    let breakIdx = 0;

    while (ballsDone < innBalls) {
      // Next session break position in this innings (if any)
      const nextBreakAt = breakIdx < breaks.length ? breaks[breakIdx] : innBalls;
      const chunk = Math.min(nextBreakAt, innBalls) - ballsDone;
      const chunkDuration = chunk * ballInterval;

      if (elapsed < timeConsumed + chunkDuration) {
        const chunkElapsed = elapsed - timeConsumed;
        const idx = ballsDone + Math.min(Math.floor(chunkElapsed / ballInterval) + 1, chunk);
        return { currentInnings: i, currentBallIdx: idx, matchEnded: false, inningsBreak: false, sessionBreak: false, breakRemaining: 0 };
      }
      timeConsumed += chunkDuration;
      ballsDone += chunk;

      // If we're at a session break position and more balls remain
      if (breakIdx < breaks.length && ballsDone === breaks[breakIdx] && ballsDone < innBalls) {
        if (elapsed < timeConsumed + sessionBreakSec) {
          const remaining = Math.ceil(timeConsumed + sessionBreakSec - elapsed);
          return { currentInnings: i, currentBallIdx: ballsDone, matchEnded: false, inningsBreak: false, sessionBreak: true, breakRemaining: remaining };
        }
        timeConsumed += sessionBreakSec;
        breakIdx++;
      }
    }

    // During break after this innings (if not last)
    if (i < ballCounts.length - 1) {
      if (elapsed < timeConsumed + breakDuration) {
        const remaining = Math.ceil(timeConsumed + breakDuration - elapsed);
        return { currentInnings: i, currentBallIdx: innBalls, matchEnded: false, inningsBreak: true, sessionBreak: false, breakRemaining: remaining };
      }
      timeConsumed += breakDuration;
    }
  }

  // Match ended
  const last = ballCounts.length - 1;
  return { currentInnings: last, currentBallIdx: ballCounts[last] || 0, matchEnded: true, inningsBreak: false, sessionBreak: false, breakRemaining: 0 };
}

export default function MatchCenter() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [commentary, setCommentary] = useState(null);
  const [result, setResult] = useState(null);
  const [rivalry, setRivalry] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0); // drives re-renders for live
  const [overlayDismissed, setOverlayDismissed] = useState(false);

  const isLiveRoute = location.pathname.endsWith('/live');
  const defaultTab = location.pathname.endsWith('/commentary') ? 'commentary' : 'scorecard';
  const [activeTab, setActiveTab] = useState(defaultTab);

  useEffect(() => {
    const tab = location.pathname.endsWith('/commentary') ? 'commentary' : 'scorecard';
    setActiveTab(tab);
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, [location.pathname]);
  const [scActiveInnings, setScActiveInnings] = useState(1);
  const [commFilter, setCommFilter] = useState('all');
  const feedRef = useRef(null);
  const userScrolledRef = useRef(false);

  const TABS = [
    { id: 'scorecard', label: 'Scorecard' },
    { id: 'commentary', label: 'Commentary' },
    { id: 'graphs', label: 'Graphs' },
    { id: 'comparison', label: 'Comparison' },
    { id: 'partnerships', label: 'Partnerships' },
    { id: 'summary', label: 'Summary' },
    { id: 'rivalry', label: 'Rivalry' },
  ];

  // ─── Determine live status from server data ───
  const isLive = result?.fixtureStatus === 'IN_PROGRESS';
  const createdAt = commentary?.createdAt || result?.createdAt;
  const ballCounts = commentary?.ballCounts || result?.ballCounts || [];
  const ballInterval = commentary?.ballIntervalSeconds || result?.ballIntervalSeconds || 5;
  const breakDuration = commentary?.inningsBreakSeconds || result?.inningsBreakSeconds || 300;
  const sessionBreakSec = commentary?.sessionBreakSeconds || result?.sessionBreakSeconds || 0;
  const sessionBreakPositions = commentary?.sessionBreakPositions || result?.sessionBreakPositions || null;

  // ─── Compute time-based position ───
  const position = useMemo(() => {
    if (!isLive) {
      const totalInnings = commentary?.innings?.length || 0;
      return {
        currentInnings: Math.max(0, totalInnings - 1),
        currentBallIdx: totalInnings > 0 ? (commentary.innings[totalInnings - 1]?.ballEvents?.length || 0) : 0,
        matchEnded: true,
        inningsBreak: false,
        sessionBreak: false,
        breakRemaining: 0,
      };
    }
    return computePosition(createdAt, ballCounts, ballInterval, breakDuration, sessionBreakSec, sessionBreakPositions);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLive, createdAt, ballCounts, ballInterval, breakDuration, sessionBreakSec, sessionBreakPositions, tick, commentary]);

  const { currentInnings, currentBallIdx, matchEnded, inningsBreak, sessionBreak, breakRemaining } = position;

  // ─── Load match data ───
  useEffect(() => {
    setCommentary(null);
    setResult(null);
    setLoading(true);
    (async () => {
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
    })();
  }, [fixtureId]);

  // ─── Tick every second for live updates ───
  useEffect(() => {
    if (!isLive || matchEnded) return;
    const id = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, [isLive, matchEnded]);

  // ─── Load rivalry data when result is available ───
  useEffect(() => {
    if (!result?.homeTeamId || !result?.awayTeamId) return;
    getRivalry(result.homeTeamId, result.awayTeamId)
      .then((res) => setRivalry(res.data))
      .catch(() => {});
  }, [result?.homeTeamId, result?.awayTeamId, result?.fixtureStatus]);

  // ─── Ball data computation ───
  const allBalls = commentary?.innings?.[currentInnings]?.ballEvents || [];
  const inn = commentary?.innings?.[currentInnings];
  const visibleBalls = isLive && !matchEnded ? allBalls.slice(0, currentBallIdx) : allBalls;

  // ─── Live score from visible balls ───
  const liveScore = useMemo(() => {
    let runs = 0, wkts = 0;
    visibleBalls.forEach((b) => { runs += b.runs; if (b.isWicket) wkts++; });
    const legal = getLegalCount(visibleBalls);
    return { runs, wkts, overs: oversDisplay(legal) };
  }, [visibleBalls]);

  // ─── Batsmen at crease & bowler ───
  const { batsmenAtCrease, currentBowler, batsmenMap, bowlerMap } = useMemo(() => {
    const batMap = buildBatsmenMap(visibleBalls);
    const bowlMap = buildBowlerMap(visibleBalls);
    const dismissed = new Set();
    visibleBalls.forEach((b) => { if (b.isWicket) dismissed.add(b.batsman); });
    const atCrease = Object.keys(batMap).filter((n) => !dismissed.has(n));
    const lastBowler = visibleBalls.length > 0 ? visibleBalls[visibleBalls.length - 1].bowler : null;
    return { batsmenAtCrease: atCrease.slice(-2), currentBowler: lastBowler, batsmenMap: batMap, bowlerMap: bowlMap };
  }, [visibleBalls]);

  // ─── Progressive scorecard data ───
  const liveScorecard = useMemo(() => {
    if (!commentary) return [];
    const innings = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (isLive && !matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (!isLive || matchEnded) {
        balls = innData.ballEvents || [];
      } else if (i < currentInnings) {
        balls = innData.ballEvents || [];
      } else {
        balls = (innData.ballEvents || []).slice(0, currentBallIdx);
      }
      innings.push(buildInningsStats(innData, balls));
    }
    return innings;
  }, [commentary, currentInnings, currentBallIdx, matchEnded, isLive]);

  // ─── Reset userScrolled when tab changes or match changes ───
  useEffect(() => {
    userScrolledRef.current = false;
  }, [activeTab, fixtureId]);

  // ─── Detect user manually scrolling the feed ───
  useEffect(() => {
    const el = feedRef.current;
    if (!el) return;
    const onScroll = () => { userScrolledRef.current = true; };
    el.addEventListener('scroll', onScroll, { passive: true });
    return () => el.removeEventListener('scroll', onScroll);
  }, [activeTab]);

  // ─── Auto-scroll feed (live only, skip if user scrolled) ───
  useEffect(() => {
    if (!feedRef.current) return;
    const liveAndRunning = isLive && !matchEnded;
    if (!liveAndRunning) return;
    if (userScrolledRef.current) return;
    feedRef.current.scrollTop = 0; // newest over is at top in live reversed layout
  }, [currentBallIdx, activeTab, isLive, matchEnded]);

  // ─── Initial scroll to top when entering live commentary ───
  useEffect(() => {
    if (!feedRef.current) return;
    if (isLive && !matchEnded && activeTab === 'commentary') {
      feedRef.current.scrollTop = 0;
    }
  }, [isLive, matchEnded, activeTab]);

  // ─── Partnerships computed ───
  const partnerships = useMemo(() => {
    if (!commentary) return [];
    const allPartners = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (isLive && !matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (!isLive || matchEnded) balls = innData.ballEvents || [];
      else if (i < currentInnings) balls = innData.ballEvents || [];
      else balls = (innData.ballEvents || []).slice(0, currentBallIdx);
      allPartners.push({ inningsNumber: i + 1, battingTeam: innData.battingTeam, partnerships: computePartnerships(balls) });
    }
    return allPartners;
  }, [commentary, currentInnings, currentBallIdx, matchEnded, isLive]);

  // ─── Graphs data ───
  const graphData = useMemo(() => {
    if (!commentary) return [];
    const inningsData = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (isLive && !matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (!isLive || matchEnded) balls = innData.ballEvents || [];
      else if (i < currentInnings) balls = innData.ballEvents || [];
      else balls = (innData.ballEvents || []).slice(0, currentBallIdx);

      const overRuns = {};
      const overWickets = {};
      balls.forEach((b) => {
        if (!overRuns[b.over]) overRuns[b.over] = 0;
        overRuns[b.over] += b.runs;
        if (b.isWicket) {
          if (!overWickets[b.over]) overWickets[b.over] = 0;
          overWickets[b.over] += 1;
        }
      });
      const maxOver = balls.length > 0 ? Math.max(...balls.map((b) => b.over)) : -1;
      const runsPerOver = [];
      const wicketsPerOver = [];
      const runRatePerOver = [];
      let cumulative = [];
      let total = 0;
      for (let o = 0; o <= maxOver; o++) {
        const r = overRuns[o] || 0;
        runsPerOver.push(r);
        wicketsPerOver.push(overWickets[o] || 0);
        total += r;
        cumulative.push(total);
        runRatePerOver.push(total / (o + 1));
      }
      inningsData.push({ battingTeam: innData.battingTeam, inningsIdx: i, runsPerOver, wicketsPerOver, runRatePerOver, cumulative });
    }
    return inningsData;
  }, [commentary, currentInnings, currentBallIdx, matchEnded, isLive]);

  // ─── Comparison data ───
  const comparisonData = useMemo(() => {
    if (!commentary) return [];
    const format = result?.format || 'T20';
    const phases = getPhases(format);
    const inningsStats = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (isLive && !matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (!isLive || matchEnded) balls = innData.ballEvents || [];
      else if (i < currentInnings) balls = innData.ballEvents || [];
      else balls = (innData.ballEvents || []).slice(0, currentBallIdx);

      let runs = 0, wkts = 0, fours = 0, sixes = 0, dots = 0, extras = 0;
      balls.forEach((b) => {
        runs += b.runs;
        if (b.isWicket) wkts++;
        if (b.isBoundary && !b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) fours++;
        if (b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) sixes++;
        if (b.runs === 0 && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye && !b.isWicket) dots++;
        if (b.isWide || b.isNoBall || b.isBye || b.isLegBye) extras += b.runs;
      });
      const legal = getLegalCount(balls);
      const rr = legal > 0 ? ((runs / legal) * 6).toFixed(2) : '0.00';

      const phaseStats = {};
      for (const [phaseName, [start, end]] of Object.entries(phases)) {
        const pBalls = balls.filter((b) => b.over >= start && b.over <= end);
        let pRuns = 0, pWkts = 0;
        pBalls.forEach((b) => { pRuns += b.runs; if (b.isWicket) pWkts++; });
        phaseStats[phaseName] = { runs: pRuns, wickets: pWkts };
      }

      inningsStats.push({
        inningsNumber: i + 1,
        battingTeam: innData.battingTeam,
        runs, wkts, fours, sixes, dots, extras, runRate: rr,
        overs: oversDisplay(legal),
        phaseStats,
      });
    }
    return inningsStats;
  }, [commentary, currentInnings, currentBallIdx, matchEnded, isLive, result?.format]);

  // ─── Summary data ───
  const summaryData = useMemo(() => {
    if (!commentary) return [];
    const inningsSummaries = [];
    const totalInnings = commentary.innings?.length || 0;
    for (let i = 0; i < totalInnings; i++) {
      if (isLive && !matchEnded && i > currentInnings) break;
      const innData = commentary.innings[i];
      let balls;
      if (!isLive || matchEnded) balls = innData.ballEvents || [];
      else if (i < currentInnings) balls = innData.ballEvents || [];
      else balls = (innData.ballEvents || []).slice(0, currentBallIdx);

      const fow = [];
      let runTotal = 0;
      balls.forEach((b) => {
        runTotal += b.runs;
        if (b.isWicket) {
          fow.push({ wicket: fow.length + 1, score: runTotal, over: b.overBall, batsman: b.batsman });
        }
      });

      const batMap = {};
      const milestones = [];
      balls.forEach((b) => {
        if (!batMap[b.batsman]) batMap[b.batsman] = 0;
        const batRunsThisBall = (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
        batMap[b.batsman] += batRunsThisBall;
        if (batMap[b.batsman] >= 100 && (batMap[b.batsman] - batRunsThisBall) < 100) {
          milestones.push({ player: b.batsman, type: '100', over: b.overBall });
        } else if (batMap[b.batsman] >= 50 && (batMap[b.batsman] - batRunsThisBall) < 50) {
          milestones.push({ player: b.batsman, type: '50', over: b.overBall });
        }
      });

      const overRuns = {};
      balls.forEach((b) => {
        if (!overRuns[b.over]) overRuns[b.over] = 0;
        overRuns[b.over] += b.runs;
      });
      let biggestOver = null;
      let biggestRuns = 0;
      for (const [o, r] of Object.entries(overRuns)) {
        if (r > biggestRuns) { biggestRuns = r; biggestOver = Number(o); }
      }

      inningsSummaries.push({
        inningsNumber: i + 1,
        battingTeam: innData.battingTeam,
        fallOfWickets: fow,
        milestones,
        biggestOver: biggestOver !== null ? { over: biggestOver, runs: biggestRuns } : null,
      });
    }
    return inningsSummaries;
  }, [commentary, currentInnings, currentBallIdx, matchEnded, isLive]);

  // ─── Performance points (MoM scoring formula) ───
  const { homePoints, awayPoints } = useMemo(() => {
    if (!result?.innings) return { homePoints: [], awayPoints: [] };
    const players = {};
    for (const inn of result.innings) {
      for (const bc of inn.battingCard || []) {
        if (!players[bc.playerId]) {
          players[bc.playerId] = { id: bc.playerId, name: bc.playerName, teamName: inn.battingTeam, batPts: 0, bowlPts: 0 };
        }
        let pts = bc.runs * 1.0 + bc.fours * 1.5 + bc.sixes * 2.0;
        if (bc.runs >= 50) pts += 15;
        if (bc.runs >= 100) pts += 30;
        players[bc.playerId].batPts += pts;
      }
      for (const bc of inn.bowlingCard || []) {
        if (!players[bc.playerId]) {
          players[bc.playerId] = { id: bc.playerId, name: bc.playerName, teamName: inn.bowlingTeam, batPts: 0, bowlPts: 0 };
        }
        let pts = bc.wickets * 20.0 + bc.maidens * 5.0 + bc.dotBalls * 0.5;
        if (bc.wickets >= 3) pts += 15;
        if (bc.wickets >= 5) pts += 30;
        const oversNum = parseFloat(bc.overs);
        if (oversNum > 0 && bc.economy < 5.0) pts += 10;
        players[bc.playerId].bowlPts += pts;
      }
    }
    const all = Object.values(players)
      .map((p) => ({ ...p, totalPts: +(p.batPts + p.bowlPts).toFixed(1), batPts: +p.batPts.toFixed(1), bowlPts: +p.bowlPts.toFixed(1) }))
      .sort((a, b) => b.totalPts - a.totalPts);
    return {
      homePoints: all.filter((p) => p.teamName === result.homeTeamName),
      awayPoints: all.filter((p) => p.teamName === result.awayTeamName),
    };
  }, [result]);

  // ─── Team Strength Breakdown (pre-computed by backend using ME formulas) ───
  const teamStrengths = result?.teamStrengths?.home && result?.teamStrengths?.away
    ? { home: result.teamStrengths.home, away: result.teamStrengths.away }
    : null;

  if (loading) return <div className="mc-loading">Loading match...</div>;
  if (!commentary || !result) return <div className="mc-loading">Match data not available.</div>;

  const battingTeam = inn?.battingTeam || '—';
  const isFC = result?.format === 'FC';
  const totalInningsCount = liveScorecard.length;
  // For limited-overs: target in 2nd innings. For FC: target in 4th innings (chase).
  const isChaseInnings = isFC ? currentInnings === 3 : currentInnings === 1;
  const firstInnScore = liveScorecard[0]?.scoreDisplay;
  const firstInnRuns = liveScorecard[0] ? parseInt(liveScorecard[0].scoreDisplay) : 0;
  let target = null;
  let need = null;
  if (!isFC && isChaseInnings) {
    target = firstInnRuns + 1;
    need = Math.max(0, target - liveScore.runs);
  } else if (isFC && currentInnings === 3 && liveScorecard.length >= 3) {
    // FC 4th innings chase: target = team1Total - team2PrevTotal + 1
    const inn1 = parseInt(liveScorecard[0]?.scoreDisplay) || 0;
    const inn2 = parseInt(liveScorecard[1]?.scoreDisplay) || 0;
    const inn3 = parseInt(liveScorecard[2]?.scoreDisplay) || 0;
    // Determine which team is chasing — 4th(idx 3) bats for chasing team
    target = (inn1 + inn3) - inn2 + 1; // simplified — same team batted 1st & 3rd unless follow-on
    need = Math.max(0, target - liveScore.runs);
  }
  const scInn = liveScorecard.find((i) => i.inningsNumber === scActiveInnings);

  const lastOverBalls = (() => {
    const curOver = visibleBalls.length > 0 ? visibleBalls[visibleBalls.length - 1].over : -1;
    return visibleBalls.filter((b) => b.over === curOver);
  })();

  // ─── Compute commentary balls for selected innings ───
  const commInn = commentary.innings?.find((i) => i.inningsNumber === scActiveInnings);
  const commBalls = (() => {
    if (!commInn) return [];
    if (!isLive || matchEnded) return commInn.ballEvents || [];
    if (scActiveInnings - 1 < currentInnings) return commInn.ballEvents || [];
    if (scActiveInnings - 1 === currentInnings) return (commInn.ballEvents || []).slice(0, currentBallIdx);
    return [];
  })();

  // ─── Compute progressive score displays for commentary/scorecard tabs ───
  const getProgressiveScore = (innIdx) => {
    const sc = liveScorecard.find((s) => s.inningsNumber === innIdx + 1);
    return sc ? sc.scoreDisplay : '—';
  };

  // Group commentary balls by over
  const commOverGroups = {};
  for (const e of commBalls) {
    if (!commOverGroups[e.over]) commOverGroups[e.over] = [];
    commOverGroups[e.over].push(e);
  }
  const liveRunning = isLive && !matchEnded;
  const commOvers = Object.keys(commOverGroups).map(Number).sort((a, b) => liveRunning ? b - a : a - b);
  const filteredCommOvers =
    commFilter === 'all' ? commOvers
    : commFilter === 'wickets' ? commOvers.filter((o) => commOverGroups[o].some((e) => e.isWicket))
    : commFilter === 'boundaries' ? commOvers.filter((o) => commOverGroups[o].some((e) => e.isBoundary || e.isSix))
    : commOvers;

  // ─── Commentary over snapshots (cumulative state at end of each over) ───
  const commOverSnapshots = (() => {
    if (!commBalls.length) return {};
    const snapshots = {};
    let cumRuns = 0, cumLegal = 0, cumWkts = 0;
    const batMap = {};   // name -> { runs, balls, dismissed }
    const bowlMap = {};  // name -> { runs, legalBalls, wickets }
    let lastWicket = null;
    let partRuns = 0, partBalls = 0;
    let prevSnOver = -1, overLegal = 0;

    for (let idx = 0; idx < commBalls.length; idx++) {
      const b = commBalls[idx];
      if (b.over !== prevSnOver) { overLegal = 0; prevSnOver = b.over; }
      const nextLegalNum = overLegal + 1;
      const displayBall = b.isWide
        ? `${b.over}.${nextLegalNum}w`
        : b.isNoBall
        ? `${b.over}.${nextLegalNum}nb`
        : `${b.over}.${nextLegalNum}`;
      if (!b.isWide && !b.isNoBall) overLegal++;
      cumRuns += b.runs;
      if (!b.isWide && !b.isNoBall) cumLegal++;

      if (!batMap[b.batsman]) batMap[b.batsman] = { runs: 0, balls: 0, dismissed: false };
      batMap[b.batsman].runs += (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
      if (!b.isWide && !b.isNoBall) batMap[b.batsman].balls++;
      else if (b.isNoBall) batMap[b.batsman].balls++;

      if (!bowlMap[b.bowler]) bowlMap[b.bowler] = { runs: 0, legalBalls: 0, wickets: 0 };
      bowlMap[b.bowler].runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
      if (!b.isWide && !b.isNoBall) bowlMap[b.bowler].legalBalls++;

      partRuns += b.runs;
      if (!b.isWide && !b.isNoBall) partBalls++;
      else if (b.isNoBall) partBalls++;

      if (b.isWicket) {
        cumWkts++;
        batMap[b.batsman].dismissed = true;
        if (b.dismissalType !== 'RUN_OUT') {
          bowlMap[b.bowler].wickets++;
        }
        lastWicket = {
          batsman: b.batsman,
          runs: batMap[b.batsman].runs,
          balls: batMap[b.batsman].balls,
          bowler: b.bowler,
          overBall: displayBall,
          score: `${cumRuns}/${cumWkts}`,
        };
        partRuns = 0;
        partBalls = 0;
      }

      const nextBall = commBalls[idx + 1];
      if (!nextBall || nextBall.over !== b.over) {
        const atCrease = Object.entries(batMap)
          .filter(([, v]) => !v.dismissed)
          .map(([name, v]) => ({ name, runs: v.runs, balls: v.balls }))
          .slice(-2);
        snapshots[b.over] = {
          cumRuns, cumWkts, cumLegal,
          atCrease,
          curBowler: b.bowler,
          curBowlerFigs: { ...bowlMap[b.bowler] },
          partRuns, partBalls,
          lastWicket: lastWicket ? { ...lastWicket } : null,
          rr: cumLegal > 0 ? (cumRuns / cumLegal) * 6 : 0,
        };
      }
    }
    return snapshots;
  })();

  // ─── Target info for chase innings in commentary ───
  const commTargetInfo = (() => {
    if (isFC || scActiveInnings !== 2) return null;
    const inn1 = liveScorecard.find((s) => s.inningsNumber === 1);
    if (!inn1) return null;
    const t = (parseInt(inn1.scoreDisplay) || 0) + 1;
    const totalBalls = result.format === 'T20' ? 120 : 300;
    return { target: t, totalBalls };
  })();

  // ─── Commentary milestones (50/100/150/200 for bat, 3W/5W for bowl) ───
  const commMilestones = (() => {
    const map = new Map();
    const batRuns = {};
    const bowlerWkts = {};
    for (const b of commBalls) {
      const batRunsThisBall = (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
      if (!batRuns[b.batsman]) batRuns[b.batsman] = 0;
      const prev = batRuns[b.batsman];
      batRuns[b.batsman] += batRunsThisBall;
      const curr = batRuns[b.batsman];
      for (const ms of [50, 100, 150, 200]) {
        if (prev < ms && curr >= ms) {
          if (!map.has(b)) map.set(b, []);
          map.get(b).push({ type: 'bat', player: b.batsman, score: ms });
        }
      }
      if (b.isWicket && b.dismissalType !== 'RUN_OUT') {
        if (!bowlerWkts[b.bowler]) bowlerWkts[b.bowler] = 0;
        bowlerWkts[b.bowler]++;
        const w = bowlerWkts[b.bowler];
        if (w === 3 || w === 5) {
          if (!map.has(b)) map.set(b, []);
          map.get(b).push({ type: 'bowl', player: b.bowler, wickets: w });
        }
      }
    }
    return map;
  })();

  // ─── Break countdown display helper ───
  const breakMinutes = Math.floor(breakRemaining / 60);
  const breakSeconds = breakRemaining % 60;

  return (
    <div className="mc-page">
      {/* ─── Match Header ─── */}
      <div className="mc-header">
        <button className="mc-back" onClick={() => navigate(-1)}>
          <HiOutlineChevronLeft />
        </button>
        <div className="mc-header-body">
          <div className="mc-teams-row">
            <div className="mc-team mc-team-clickable" onClick={() => navigate(`/team/${result.homeTeamId}`)}>
              {result.homeTeamPicUrl ? (
                <img src={fileUrl(result.homeTeamPicUrl)} alt="" className="mc-team-logo" />
              ) : (
                <span className="mc-team-initials">{result.homeTeamName?.slice(0, 2).toUpperCase()}</span>
              )}
              <span className="mc-team-name">{result.homeTeamName}</span>
            </div>
            <span className="mc-vs">vs</span>
            <div className="mc-team mc-team-clickable" onClick={() => navigate(`/team/${result.awayTeamId}`)}>
              {result.awayTeamPicUrl ? (
                <img src={fileUrl(result.awayTeamPicUrl)} alt="" className="mc-team-logo" />
              ) : (
                <span className="mc-team-initials">{result.awayTeamName?.slice(0, 2).toUpperCase()}</span>
              )}
              <span className="mc-team-name">{result.awayTeamName}</span>
            </div>
            <span className={`mc-status-badge ${isLive && !matchEnded ? 'live' : 'completed'}`}>
              {isLive && !matchEnded ? 'In Progress' : 'Completed'}
            </span>
          </div>
          <div className="mc-meta-row">
            {result.groundName && (
              <span className="mc-meta-item"><HiOutlineMapPin /> {result.groundName}</span>
            )}
            {result.leagueId && result.leagueLabel && (
              <span
                className="mc-meta-item mc-meta-link"
                role="button"
                tabIndex={0}
                onClick={() => navigate(`/league/${result.leagueId}`)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') navigate(`/league/${result.leagueId}`);
                }}
              >
                <HiOutlineClipboardDocumentList /> League {result.leagueLabel}
              </span>
            )}
            {result.matchDate && (
              <span className="mc-meta-item mc-meta-date">
                <HiOutlineCalendarDays />
                {new Date(result.matchDate + 'T00:00:00').toLocaleDateString('en-US', {
                  weekday: 'short', day: 'numeric', month: 'short', year: 'numeric'
                })}
                {result.matchStartTimeUtc && ` at ${result.matchStartTimeUtc} UTC`}
              </span>
            )}
            {result.format && <span className="mc-format-badge">{result.format}</span>}
            {result.pitchType && (
              <span className="mc-meta-item">
                Pitch: <strong>{result.pitchType}</strong>
              </span>
            )}
            {result.weather?.available && (
              <span className="mc-meta-item">
                {result.weather.icon} {result.weather.condition} {result.weather.temperature}C
              </span>
            )}
            {result.attendance != null && result.attendance > 0 && (
              <span className="mc-meta-item"><HiOutlineUserGroup /> {result.attendance.toLocaleString()} attendance</span>
            )}
          </div>
          <div className="mc-toss-banner">
            <span className="mc-toss-icon">🏏</span>
            <span>
              <strong>{result.tossWinner}</strong> won the toss and decided to{' '}
              <strong>{result.tossDecision?.toLowerCase()}</strong> first.
            </span>
          </div>
        </div>
      </div>

      {/* ─── Live Score Bar ─── */}
      {isLive && !matchEnded && !inningsBreak && !sessionBreak && (
        <div className="mc-live-bar">
          <div className="mc-live-info">
            <span className="mc-live-team">{battingTeam}</span>
            <span className="mc-live-score">{liveScore.runs}/{liveScore.wkts}</span>
            <span className="mc-live-overs">({liveScore.overs} ov)</span>
            {target && <span className="mc-live-need">Need {need} runs</span>}
          </div>
          <div className="mc-live-strip">
            {batsmenAtCrease.map((name) => {
              const stats = batsmenMap[name] || { runs: 0, balls: 0 };
              const isStriker = visibleBalls.length > 0 && visibleBalls[visibleBalls.length - 1].batsman === name;
              return (
                <span key={name} className={`mc-strip-bat ${isStriker ? 'striker' : ''}`}>
                  {name} {stats.runs}({stats.balls}){isStriker ? '*' : ''}
                </span>
              );
            })}
            {currentBowler && bowlerMap[currentBowler] && (
              <span className="mc-strip-bowler">
                {currentBowler} {bowlerMap[currentBowler].wickets}-{bowlerMap[currentBowler].runs}
              </span>
            )}
          </div>
          <div className="mc-live-over">
            <span className="mc-over-label">This over:</span>
            {lastOverBalls.map((b, i) => (
              <span key={i} className={`mc-chip ${chipClass(b)}`}>{chipText(b)}</span>
            ))}
          </div>
        </div>
      )}

      {/* ─── Innings Break Banner ─── */}
      {isLive && inningsBreak && (
        <div className="mc-break-bar">
          <div className="mc-break-info">
            <span className="mc-break-label">Innings Break</span>
            <span className="mc-break-score">{liveScorecard[currentInnings]?.battingTeam}: {liveScorecard[currentInnings]?.scoreDisplay}</span>
          </div>
          <div className="mc-break-countdown">
            <span className="mc-break-timer">
              {String(breakMinutes).padStart(2, '0')}:{String(breakSeconds).padStart(2, '0')}
            </span>
            {commentary.innings[currentInnings + 1] && (
              <span className="mc-break-sub">
                {commentary.innings[currentInnings + 1]?.battingTeam} up next
              </span>
            )}
          </div>
        </div>
      )}

      {/* ─── Session Break Banner ─── */}
      {isLive && sessionBreak && (
        <div className="mc-break-bar">
          <div className="mc-break-info">
            <span className="mc-break-label">Session Break</span>
            <span className="mc-break-score">{liveScorecard[currentInnings]?.battingTeam}: {liveScorecard[currentInnings]?.scoreDisplay}</span>
          </div>
          <div className="mc-break-countdown">
            <span className="mc-break-timer">
              {String(breakMinutes).padStart(2, '0')}:{String(breakSeconds).padStart(2, '0')}
            </span>
          </div>
        </div>
      )}

      {/* ─── Tab Bar ─── */}
      <div className="mc-tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            className={`mc-tab ${activeTab === t.id ? 'active' : ''}`}
            onClick={() => setActiveTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* ════════ TAB CONTENT ════════ */}
      <div className="mc-content">

        {/* ═══ SCORECARD ═══ */}
        {activeTab === 'scorecard' && (
          <div className="mc-scorecard">
            {matchEnded && result.summary && (
              <div className="mc-result-bar">{result.summary}</div>
            )}
            <div className="mc-sc-tabs">
              {liveScorecard.map((rInn) => (
                <button key={rInn.inningsNumber}
                  className={`mc-sc-tab ${scActiveInnings === rInn.inningsNumber ? 'active' : ''}`}
                  onClick={() => setScActiveInnings(rInn.inningsNumber)}>
                  <span>{rInn.battingTeam}</span>
                  <span className="mc-sc-tab-score">{rInn.scoreDisplay}</span>
                </button>
              ))}
            </div>
            {scInn && (
              <>
                <div className="mc-sc-section">
                  <h3>Batting</h3>
                  <div className="mc-sc-table mc-sc-batting">
                    <div className="mc-sc-head">
                      <span className="mc-sc-name">Batter</span>
                      <span className="mc-sc-num">R</span>
                      <span className="mc-sc-num">B</span>
                      <span className="mc-sc-num">4s</span>
                      <span className="mc-sc-num">6s</span>
                      <span className="mc-sc-num mc-sc-dots">0s</span>
                      <span className="mc-sc-num">SR</span>
                    </div>
                    {scInn.battingCard?.map((bc, i) => (
                      <div key={i} className={`mc-sc-row ${bc.notOut ? 'mc-sc-notout' : ''}`}>
                        <span className="mc-sc-name mc-sc-name-link" onClick={() => navigate(`/player/${bc.playerId}`)}>
                          {bc.playerName}{bc.notOut ? '*' : ''}
                          <span className="mc-sc-dismissal">
                            {bc.notOut ? 'not out' : formatDismissal(bc)}
                          </span>
                        </span>
                        <span className={`mc-sc-num ${bc.runs >= 50 ? 'mc-sc-milestone' : ''}`}>{bc.runs}</span>
                        <span className="mc-sc-num">{bc.balls}</span>
                        <span className="mc-sc-num">{bc.fours}</span>
                        <span className="mc-sc-num">{bc.sixes}</span>
                        <span className="mc-sc-num mc-sc-dots">{bc.dots}</span>
                        <span className="mc-sc-num">{bc.strikeRate?.toFixed(1)}</span>
                      </div>
                    ))}
                    <div className="mc-sc-footer">
                      <span>Extras: {scInn.extras} <span className="mc-sc-extras-detail">(w {scInn.extWides}, nb {scInn.extNoBalls}, b {scInn.extByes}, lb {scInn.extLegByes})</span></span>
                      <span>Total: {scInn.scoreDisplay}</span>
                    </div>
                    {(() => {
                      const sInn = summaryData.find(s => s.inningsNumber === scActiveInnings);
                      if (!sInn || sInn.fallOfWickets.length === 0) return null;
                      return (
                        <div className="mc-fow">
                          <h4>Fall of Wickets</h4>
                          <div className="mc-fow-list">
                            {sInn.fallOfWickets.map((fw) => (
                              <span key={fw.wicket} className="mc-fow-item">
                                {fw.score}/{fw.wicket} <span className="mc-fow-name">({fw.batsman}, {fw.over})</span>
                              </span>
                            ))}
                          </div>
                        </div>
                      );
                    })()}
                  </div>
                </div>
                <div className="mc-sc-section">
                  <h3>Bowling</h3>
                  <div className="mc-sc-table mc-sc-bowling">
                    <div className="mc-sc-head">
                      <span className="mc-sc-name">Bowler</span>
                      <span className="mc-sc-num">O</span>
                      <span className="mc-sc-num">M</span>
                      <span className="mc-sc-num">R</span>
                      <span className="mc-sc-num">W</span>
                      <span className="mc-sc-num mc-sc-dots">0s</span>
                      <span className="mc-sc-num mc-sc-wd">Wd</span>
                      <span className="mc-sc-num mc-sc-nb">Nb</span>
                      <span className="mc-sc-num">Econ</span>
                    </div>
                    {scInn.bowlingCard?.map((bc, i) => (
                      <div key={i} className={`mc-sc-row ${bc.wickets >= 3 ? 'mc-sc-haul' : ''}`}>
                        <span className="mc-sc-name mc-sc-name-link" onClick={() => navigate(`/player/${bc.playerId}`)}>{bc.playerName}</span>
                        <span className="mc-sc-num">{bc.overs}</span>
                        <span className="mc-sc-num">{bc.maidens}</span>
                        <span className="mc-sc-num">{bc.runs}</span>
                        <span className={`mc-sc-num ${bc.wickets >= 3 ? 'mc-sc-milestone' : ''}`}>{bc.wickets}</span>
                        <span className="mc-sc-num mc-sc-dots">{bc.dots}</span>
                        <span className="mc-sc-num mc-sc-wd">{bc.wides}</span>
                        <span className="mc-sc-num mc-sc-nb">{bc.noBalls}</span>
                        <span className="mc-sc-num">{bc.economy?.toFixed(1)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        {/* ═══ COMMENTARY ═══ */}
        {activeTab === 'commentary' && (
          <div className="mc-commentary">
            <div className="mc-comm-controls">
              <div className="mc-comm-inn-tabs">
                {(commentary.innings || []).map((cInn, idx) => {
                  if (isLive && !matchEnded && idx > currentInnings) return null;
                  return (
                    <button key={cInn.inningsNumber}
                      className={`mc-comm-inn-tab ${scActiveInnings === cInn.inningsNumber ? 'active' : ''}`}
                      onClick={() => setScActiveInnings(cInn.inningsNumber)}>
                      {cInn.battingTeam} — {getProgressiveScore(idx)}
                    </button>
                  );
                })}
              </div>
              <div className="mc-comm-filters">
                {['all', 'wickets', 'boundaries'].map((f) => (
                  <button key={f} className={`mc-comm-filter ${commFilter === f ? 'active' : ''}`} onClick={() => setCommFilter(f)}>
                    {f === 'all' ? 'All' : f === 'wickets' ? 'Wickets' : 'Boundaries'}
                  </button>
                ))}
              </div>
            </div>
            <div className="mc-comm-feed" ref={feedRef}>
              {filteredCommOvers.length === 0 ? (
                <div className="mc-comm-empty">{isLive && visibleBalls.length === 0 ? 'Match starting...' : 'No events match filter.'}</div>
              ) : (
                filteredCommOvers.map((overNum) => {
                  const allBalls = commOverGroups[overNum];
                  // Build legal-ball display labels for each delivery in this over
                  const ballDisplayMap = new Map();
                  let overLegalCount = 0;
                  for (const ball of allBalls) {
                    const nextNum = overLegalCount + 1;
                    if (ball.isWide) {
                      ballDisplayMap.set(ball, `${overNum}.${nextNum}w`);
                    } else if (ball.isNoBall) {
                      ballDisplayMap.set(ball, `${overNum}.${nextNum}nb`);
                    } else {
                      overLegalCount++;
                      ballDisplayMap.set(ball, `${overNum}.${overLegalCount}`);
                    }
                  }
                  const filteredBalls = commFilter === 'wickets'
                    ? allBalls.filter((b) => b.isWicket)
                    : commFilter === 'boundaries'
                    ? allBalls.filter((b) => b.isBoundary || b.isSix)
                    : allBalls;
                  const balls = liveRunning ? [...filteredBalls].reverse() : filteredBalls;
                  const overRuns = balls.reduce((s, b) => s + b.runs, 0);
                  const overWickets = balls.filter((b) => b.isWicket).length;
                  const snap = commOverSnapshots[overNum];
                  const ctxPanel = snap ? (() => {
                    const need = commTargetInfo ? Math.max(0, commTargetInfo.target - snap.cumRuns) : null;
                    const remainBalls = commTargetInfo ? Math.max(0, commTargetInfo.totalBalls - snap.cumLegal) : null;
                    const rrr = commTargetInfo && remainBalls > 0 ? (need / remainBalls) * 6 : null;
                    const battingTeamLabel = commInn?.battingTeam || 'Team';
                    return (
                      <div className="mc-comm-over-ctx">
                        <div className="mc-comm-ctx-grid">
                          <div className="mc-comm-ctx-col mc-comm-ctx-col-left">
                            <div className="mc-comm-ctx-row mc-comm-ctx-score-row">
                              <span className="mc-comm-ctx-item">
                                <span className="mc-comm-ctx-label">Score</span>
                                <span>{battingTeamLabel} {snap.cumRuns}/{snap.cumWkts}</span>
                              </span>
                            </div>
                            <div className="mc-comm-ctx-row mc-comm-ctx-players">
                              <div className="mc-comm-ctx-bats">
                                {snap.atCrease.map((bat) => (
                                  <span key={bat.name} className="mc-comm-ctx-bat">
                                    {bat.name} <strong>{bat.runs}</strong>({bat.balls})
                                  </span>
                                ))}
                              </div>
                            </div>
                            <div className="mc-comm-ctx-row mc-comm-ctx-wkt-row">
                              <span className="mc-comm-ctx-item">
                                <span className="mc-comm-ctx-label">Pship</span>
                                <span>{snap.partRuns}({snap.partBalls})</span>
                              </span>
                              {snap.lastWicket && (
                                <span className="mc-comm-ctx-item mc-comm-ctx-lastwkt">
                                  <span className="mc-comm-ctx-label">Last wkt</span>
                                  <span>
                                    {snap.lastWicket.score} - {snap.lastWicket.batsman} {snap.lastWicket.runs}({snap.lastWicket.balls}) b {snap.lastWicket.bowler}
                                    <span className="mc-comm-ctx-over"> ({snap.lastWicket.overBall})</span>
                                  </span>
                                </span>
                              )}
                            </div>
                          </div>

                          <div className="mc-comm-ctx-col mc-comm-ctx-col-right">
                            <div className="mc-comm-ctx-row">
                              <span className="mc-comm-ctx-bowl">
                                {snap.curBowler} {oversDisplay(snap.curBowlerFigs.legalBalls)}-{snap.curBowlerFigs.wickets}-{snap.curBowlerFigs.runs}
                              </span>
                            </div>
                            <div className="mc-comm-ctx-row mc-comm-ctx-rates">
                              <span><span className="mc-comm-ctx-label">RR</span> {snap.rr.toFixed(2)}</span>
                              {commTargetInfo && (
                                <>
                                  <span><span className="mc-comm-ctx-label">RRR</span> {rrr !== null ? rrr.toFixed(2) : '-'}</span>
                                  <span><span className="mc-comm-ctx-label">Need</span> {need !== null ? `${need} in ${remainBalls}b` : '-'}</span>
                                </>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })() : null;
                  return (
                    <div key={overNum} className="mc-comm-over-group">
                      {liveRunning && commFilter === 'all' && (
                        <div className="mc-comm-over-header">
                          <span className="mc-comm-over-label">Over {overNum + 1}</span>
                          <span className="mc-comm-over-summary">
                            {overRuns} run{overRuns !== 1 ? 's' : ''}
                            {overWickets > 0 && `, ${overWickets} wkt${overWickets > 1 ? 's' : ''}`}
                          </span>
                          <div className="mc-comm-chips">
                            {filteredBalls.map((b, i) => (
                              <span key={i} className={`mc-chip ${chipClass(b)}`}>{chipText(b)}</span>
                            ))}
                          </div>
                        </div>
                      )}
                      {liveRunning && commFilter === 'all' && ctxPanel}
                      <div className="mc-comm-ball-list">
                        {balls.map((b, i) => (
                          <Fragment key={i}>
                            <div className={`mc-comm-ball-row ${b.isWicket ? 'mc-row-wicket' : b.isSix ? 'mc-row-six' : b.isBoundary ? 'mc-row-four' : ''}`}>
                              <span className="mc-comm-ball-num">{ballDisplayMap.get(b) ?? b.overBall}</span>
                              <span className="mc-comm-ball-players">{b.bowler} to {b.batsman}</span>
                              <span className={`mc-comm-ball-result ${ballResultClass(b)}`}>{ballResultText(b)}</span>
                              {b.commentary && <span className="mc-comm-ball-text">{b.commentary}</span>}
                            </div>
                            {commMilestones.has(b) && commMilestones.get(b).map((m, mi) => (
                              <div key={`ms-${i}-${mi}`} className={`mc-comm-milestone ${m.type === 'bat' ? `mc-ms-bat mc-ms-bat-${m.score}` : `mc-ms-bowl mc-ms-bowl-${m.wickets}w`}`}>
                                {m.type === 'bat' ? (
                                  <>
                                    <span className="mc-ms-icon">{m.score >= 100 ? '💯' : '⭐'}</span>
                                    <span className="mc-ms-text">
                                      <strong>{m.player}</strong> brings up {m.score === 150 ? 'a brilliant' : m.score === 200 ? 'a magnificent' : 'a'} <strong>{m.score}</strong>!
                                    </span>
                                  </>
                                ) : (
                                  <>
                                    <span className="mc-ms-icon">{m.wickets === 5 ? '🔥' : '💥'}</span>
                                    <span className="mc-ms-text">
                                      <strong>{m.player}</strong> completes {m.wickets === 5 ? 'a magnificent FIVE-wicket haul' : 'a THREE-wicket haul'}!
                                    </span>
                                  </>
                                )}
                              </div>
                            ))}
                          </Fragment>
                        ))}
                      </div>
                      {!liveRunning && commFilter === 'all' && (
                        <div className="mc-comm-over-header">
                          <span className="mc-comm-over-label">Over {overNum + 1}</span>
                          <span className="mc-comm-over-summary">
                            {overRuns} run{overRuns !== 1 ? 's' : ''}
                            {overWickets > 0 && `, ${overWickets} wkt${overWickets > 1 ? 's' : ''}`}
                          </span>
                          <div className="mc-comm-chips">
                            {filteredBalls.map((b, i) => (
                              <span key={i} className={`mc-chip ${chipClass(b)}`}>{chipText(b)}</span>
                            ))}
                          </div>
                        </div>
                      )}
                      {!liveRunning && commFilter === 'all' && ctxPanel}
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}

        {/* ═══ GRAPHS ═══ */}
        {activeTab === 'graphs' && (
          <div className="mc-graphs">
            <h3 className="mc-section-title">Manhattan Chart <span className="mc-section-sub">Runs & Wickets per Over</span></h3>
            <ManhattanChart data={graphData} format={result.format} />
            <h3 className="mc-section-title">Run Rate Chart <span className="mc-section-sub">Cumulative RR per Over</span></h3>
            <RunRateChart data={graphData} format={result.format} />
            <h3 className="mc-section-title">Worm Chart <span className="mc-section-sub">Cumulative Runs</span></h3>
            <WormChart data={graphData} format={result.format} />
          </div>
        )}

        {/* ═══ COMPARISON ═══ */}
        {activeTab === 'comparison' && (
          <div className="mc-comparison">
            {(() => {
              const isFC = result?.format === 'FC';
              // For FC, aggregate by team across both innings
              if (isFC && comparisonData.length >= 2) {
                const teamMap = {};
                comparisonData.forEach((inn) => {
                  if (!teamMap[inn.battingTeam]) {
                    teamMap[inn.battingTeam] = { battingTeam: inn.battingTeam, runs: 0, wkts: 0, fours: 0, sixes: 0, dots: 0, extras: 0, phaseStats: {}, innings: [] };
                  }
                  const agg = teamMap[inn.battingTeam];
                  agg.runs += inn.runs;
                  agg.wkts += inn.wkts;
                  agg.fours += inn.fours;
                  agg.sixes += inn.sixes;
                  agg.dots += inn.dots;
                  agg.extras += inn.extras;
                  agg.innings.push(inn);
                  for (const [phase, ps] of Object.entries(inn.phaseStats)) {
                    if (!agg.phaseStats[phase]) agg.phaseStats[phase] = { runs: 0, wickets: 0 };
                    agg.phaseStats[phase].runs += ps.runs;
                    agg.phaseStats[phase].wickets += ps.wickets;
                  }
                });
                const teams = Object.values(teamMap);
                if (teams.length < 2) return <div className="mc-empty">Comparison available after both teams have batted.</div>;
                const t0 = teams[0];
                const t1 = teams[1];
                return (
                  <>
                    <div className="mc-comp-header">
                      <span className="mc-comp-team">{t0.battingTeam}</span>
                      <span className="mc-comp-vs">vs</span>
                      <span className="mc-comp-team">{t1.battingTeam}</span>
                    </div>

                    {/* Per-innings breakdown */}
                    <h3 className="mc-section-title mc-phase-title">Innings Breakdown</h3>
                    {t0.innings.map((inn, idx) => {
                      const otherInn = t1.innings[idx];
                      if (!otherInn) return null;
                      return (
                        <div key={idx} className="mc-phase-block">
                          <span className="mc-phase-label">Innings {idx * 2 + 1} vs {idx * 2 + 2}</span>
                          <div className="mc-phase-row">
                            <span className="mc-phase-val">{inn.runs}/{inn.wkts} ({inn.overs} ov)</span>
                            <span className="mc-phase-val">{otherInn.runs}/{otherInn.wkts} ({otherInn.overs} ov)</span>
                          </div>
                        </div>
                      );
                    })}

                    <h3 className="mc-section-title mc-phase-title">Phase Breakdown (Aggregate)</h3>
                    {Object.keys(t0.phaseStats).map((phase) => (
                      <div key={phase} className="mc-phase-block">
                        <span className="mc-phase-label">{phase.charAt(0).toUpperCase() + phase.slice(1)}</span>
                        <div className="mc-phase-row">
                          <span className="mc-phase-val">
                            {t0.phaseStats[phase].runs}/{t0.phaseStats[phase].wickets}
                          </span>
                          <span className="mc-phase-val">
                            {t1.phaseStats[phase]?.runs ?? 0}/{t1.phaseStats[phase]?.wickets ?? 0}
                          </span>
                        </div>
                      </div>
                    ))}

                    <h3 className="mc-section-title mc-phase-title">Head to Head (Aggregate)</h3>
                    {[
                      { label: 'Total', k1: 'runs' },
                      { label: 'Wickets', k1: 'wkts' },
                      { label: 'Fours', k1: 'fours' },
                      { label: 'Sixes', k1: 'sixes' },
                      { label: 'Extras', k1: 'extras' },
                      { label: 'Dots', k1: 'dots' },
                    ].map(({ label, k1 }) => (
                      <ComparisonRow key={label} label={label}
                        val1={t0[k1]} val2={t1[k1]} />
                    ))}
                  </>
                );
              }

              // T20/ODI: original 2-innings comparison
              if (comparisonData.length >= 2) {
                return (
                  <>
                    <div className="mc-comp-header">
                      <span className="mc-comp-team">{comparisonData[0].battingTeam}</span>
                      <span className="mc-comp-vs">vs</span>
                      <span className="mc-comp-team">{comparisonData[1].battingTeam}</span>
                    </div>

                    {/* ═══ Over-by-Over Comparison ═══ */}
                    {graphData.length >= 2 && (() => {
                      const totalOvers = result.format === 'T20' ? 20 : 50;
                      const target = graphData[0].cumulative.length > 0
                        ? graphData[0].cumulative[graphData[0].cumulative.length - 1] + 1
                        : null;
                      const maxLen = Math.max(graphData[0].runsPerOver.length, graphData[1].runsPerOver.length);
                      return (
                        <>
                          <h3 className="mc-section-title mc-phase-title">Over-by-Over Comparison</h3>
                          <div className="mc-obo-wrap">
                            <div className="mc-obo-table">
                              <div className="mc-obo-head mc-obo-team-head">
                                <span className="mc-obo-cell mc-obo-over"></span>
                                <span className="mc-obo-cell mc-obo-team-span" style={{ gridColumn: 'span 3' }}>{graphData[0].battingTeam}</span>
                                <span className="mc-obo-cell mc-obo-divider"></span>
                                <span className="mc-obo-cell mc-obo-team-span" style={{ gridColumn: 'span 3' }}>{graphData[1].battingTeam}</span>
                                <span className="mc-obo-cell"></span>
                              </div>
                              <div className="mc-obo-head">
                                <span className="mc-obo-cell mc-obo-over">Over</span>
                                <span className="mc-obo-cell">Runs</span>
                                <span className="mc-obo-cell mc-obo-cum">Cum</span>
                                <span className="mc-obo-cell mc-obo-cum">RR</span>
                                <span className="mc-obo-cell mc-obo-divider"></span>
                                <span className="mc-obo-cell">Runs</span>
                                <span className="mc-obo-cell mc-obo-cum">Cum</span>
                                <span className="mc-obo-cell mc-obo-cum">RR</span>
                                <span className="mc-obo-cell mc-obo-rrr">RRR</span>
                              </div>
                              {Array.from({ length: maxLen }, (_, i) => {
                                const r0 = graphData[0].runsPerOver[i];
                                const r1 = graphData[1].runsPerOver[i];
                                const c0 = graphData[0].cumulative[i];
                                const c1 = graphData[1].cumulative[i];
                                const rr0 = graphData[0].runRatePerOver[i];
                                const rr1 = graphData[1].runRatePerOver[i];
                                const oversLeft = totalOvers - (i + 1);
                                const rrr = (target != null && c1 != null && oversLeft > 0)
                                  ? ((target - c1) / oversLeft).toFixed(2)
                                  : (target != null && c1 != null && c1 >= target) ? '—' : '-';
                                return (
                                  <div key={i} className="mc-obo-row">
                                    <span className="mc-obo-cell mc-obo-over">{i + 1}</span>
                                    <span className={`mc-obo-cell ${r0 != null && r1 != null && r0 > r1 ? 'mc-obo-lead' : ''}`}>{r0 ?? '-'}</span>
                                    <span className="mc-obo-cell mc-obo-cum">{c0 ?? '-'}</span>
                                    <span className="mc-obo-cell mc-obo-cum">{rr0 != null ? rr0.toFixed(2) : '-'}</span>
                                    <span className="mc-obo-cell mc-obo-divider"></span>
                                    <span className={`mc-obo-cell ${r0 != null && r1 != null && r1 > r0 ? 'mc-obo-lead' : ''}`}>{r1 ?? '-'}</span>
                                    <span className="mc-obo-cell mc-obo-cum">{c1 ?? '-'}</span>
                                    <span className="mc-obo-cell mc-obo-cum">{rr1 != null ? rr1.toFixed(2) : '-'}</span>
                                    <span className={`mc-obo-cell mc-obo-rrr ${rrr !== '-' && rrr !== '—' && parseFloat(rrr) > 12 ? 'mc-obo-danger' : ''}`}>{rrr}</span>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        </>
                      );
                    })()}

                    <h3 className="mc-section-title mc-phase-title">Phase Breakdown</h3>
                    {Object.keys(comparisonData[0].phaseStats).map((phase) => (
                      <div key={phase} className="mc-phase-block">
                        <span className="mc-phase-label">{phase.charAt(0).toUpperCase() + phase.slice(1)}</span>
                        <div className="mc-phase-row">
                          <span className="mc-phase-val">
                            {comparisonData[0].phaseStats[phase].runs}/{comparisonData[0].phaseStats[phase].wickets}
                          </span>
                          <span className="mc-phase-val">
                            {comparisonData[1].phaseStats[phase].runs}/{comparisonData[1].phaseStats[phase].wickets}
                          </span>
                        </div>
                      </div>
                    ))}

                    <h3 className="mc-section-title mc-phase-title">Head to Head</h3>
                    {[
                      { label: 'Total', k1: 'runs' },
                      { label: 'Wickets', k1: 'wkts' },
                      { label: 'Run Rate', k1: 'runRate' },
                      { label: 'Fours', k1: 'fours' },
                      { label: 'Sixes', k1: 'sixes' },
                      { label: 'Extras', k1: 'extras' },
                      { label: 'Dots', k1: 'dots' },
                    ].map(({ label, k1 }) => (
                      <ComparisonRow key={label} label={label}
                        val1={comparisonData[0][k1]} val2={comparisonData[1][k1]} />
                    ))}
                  </>
                );
              }

              return <div className="mc-empty">Comparison available after both innings.</div>;
            })()}
          </div>
        )}

        {/* ═══ PARTNERSHIPS ═══ */}
        {activeTab === 'partnerships' && (
          <div className="mc-partnerships">
            {partnerships.map((pInn) => (
              <div key={pInn.inningsNumber} className="mc-part-innings">
                <h3 className="mc-section-title">{pInn.battingTeam} — Partnerships</h3>
                {pInn.partnerships.length === 0 ? (
                  <div className="mc-empty">No partnerships yet.</div>
                ) : (
                  <div className="mc-part-table">
                    <div className="mc-part-head">
                      <span className="mc-part-num">#</span>
                      <span className="mc-part-name">Batsman 1</span>
                      <span className="mc-part-name">Batsman 2</span>
                      <span className="mc-part-stat">Runs</span>
                      <span className="mc-part-stat">Balls</span>
                    </div>
                    {pInn.partnerships.map((p, i) => {
                      const maxRuns = Math.max(...pInn.partnerships.map((pp) => pp.runs), 1);
                      const barWidth = (p.runs / maxRuns) * 100;
                      const batTotal = p.bat1Runs + p.bat2Runs;
                      const total = batTotal + p.extras;
                      const b1Pct = total > 0 ? (p.bat1Runs / total) * 100 : 0;
                      const b2Pct = total > 0 ? (p.bat2Runs / total) * 100 : 0;
                      const exPct = total > 0 ? (p.extras / total) * 100 : 0;
                      return (
                        <div key={i} className="mc-part-row">
                          <span className="mc-part-num">{i + 1}</span>
                          <span className="mc-part-name">
                            {p.bat1}
                            <span className="mc-part-contrib">{p.bat1Runs} ({p.bat1Balls})</span>
                          </span>
                          <span className="mc-part-name">
                            {p.bat2}
                            <span className="mc-part-contrib">{p.bat2Runs} ({p.bat2Balls})</span>
                          </span>
                          <span className="mc-part-stat mc-part-runs">{p.runs}{p.extras > 0 ? <span className="mc-part-extras-hint"> ({p.extras}e)</span> : ''}</span>
                          <span className="mc-part-stat">{p.balls}</span>
                          <div className="mc-part-bar-wrap">
                            <div className="mc-part-bar-split" style={{ width: `${barWidth}%` }}>
                              <div className="mc-part-bar-b1" style={{ width: `${b1Pct}%` }} />
                              <div className="mc-part-bar-b2" style={{ width: `${b2Pct}%` }} />
                              {exPct > 0 && <div className="mc-part-bar-ex" style={{ width: `${exPct}%` }} />}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* ═══ SUMMARY ═══ */}
        {activeTab === 'summary' && (
          <div className="mc-summary">
            {matchEnded && result.summary && (
              <div className="mc-summary-result">
                <HiOutlineTrophy className="mc-summary-icon" />
                <span>{result.summary}</span>
              </div>
            )}
            {matchEnded && result.manOfMatch && (
              <div className="mc-summary-motm">
                <span className="mc-motm-label">Player of the Match</span>
                <span className="mc-motm-name" style={{ cursor: 'pointer' }} onClick={() => navigate(`/player/${result.manOfMatchId}`)}>{result.manOfMatch}</span>
              </div>
            )}
            {summaryData.map((sInn) => (
              <div key={sInn.inningsNumber} className="mc-summary-innings">
                <h3 className="mc-section-title">{sInn.battingTeam}</h3>
                {sInn.milestones.length > 0 && (
                  <div className="mc-milestones">
                    <h4>Milestones</h4>
                    {sInn.milestones.map((m, i) => (
                      <span key={i} className={`mc-milestone-badge ${m.type === '100' ? 'century' : 'fifty'}`}>
                        {m.player} — {m.type} (ov {m.over})
                      </span>
                    ))}
                  </div>
                )}
                {sInn.biggestOver && (
                  <div className="mc-biggest-over">
                    <h4>Biggest Over</h4>
                    <span>Over {sInn.biggestOver.over} — {sInn.biggestOver.runs} runs</span>
                  </div>
                )}
              </div>
            ))}

            {/* Performance Points */}
            {matchEnded && (homePoints.length > 0 || awayPoints.length > 0) && (
              <div className="mc-perf-section">
                <h3 className="mc-section-title">⭐ Performance Points</h3>
                <div className="mc-perf-teams">
                  {[{ label: result.homeTeamName, players: homePoints }, { label: result.awayTeamName, players: awayPoints }].map((team) => (
                    <div className="mc-perf-team" key={team.label}>
                      <div className="mc-perf-team-header">{team.label}</div>
                      <div className="mc-perf-table">
                        <div className="mc-perf-head">
                          <span className="mc-perf-col-rank">#</span>
                          <span className="mc-perf-col-name">Player</span>
                          <span className="mc-perf-col-num">Bat</span>
                          <span className="mc-perf-col-num">Bowl</span>
                          <span className="mc-perf-col-total">Total</span>
                        </div>
                        {team.players.map((p, i) => (
                          <div
                            key={p.id}
                            className={`mc-perf-row ${p.id === result.manOfMatchId ? 'mc-perf-motm' : ''}`}
                            onClick={() => navigate(`/player/${p.id}`)}
                          >
                            <span className="mc-perf-col-rank">{i + 1}</span>
                            <span className="mc-perf-col-name">
                              {p.name}
                              {p.id === result.manOfMatchId && <span className="mc-perf-motm-badge">MoM</span>}
                            </span>
                            <span className="mc-perf-col-num">{p.batPts}</span>
                            <span className="mc-perf-col-num">{p.bowlPts}</span>
                            <span className="mc-perf-col-total">{p.totalPts}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Team Strength Breakdown */}
            {matchEnded && teamStrengths && (
              <div className="mc-strength-section">
                <h3 className="mc-section-title">💪 Team Strength Breakdown</h3>
                <div className="mc-strength-teams">
                  {[
                    { label: result.homeTeamName, data: teamStrengths.home },
                    { label: result.awayTeamName, data: teamStrengths.away },
                  ].map((team) => {
                    const d = team.data;
                    const rows = [
                      { label: 'Top Order (1-3)', value: d.topOrder, cls: 'bat' },
                      { label: 'Middle Order (4-7)', value: d.middleOrder, cls: 'bat' },
                      { label: 'Lower Order (8-11)', value: d.lowerOrder, cls: 'bat' },
                      { label: `Seam Bowling (${d.seamCount})`, value: d.seamBowling, cls: 'bowl' },
                      { label: `Spin Bowling (${d.spinCount})`, value: d.spinBowling, cls: 'bowl' },
                      { label: 'Fielding', value: d.fldComponent, cls: 'fld' },
                    ];
                    const maxVal = Math.max(...rows.map((r) => r.value), 1);
                    return (
                      <div className="mc-strength-team" key={team.label}>
                        <div className="mc-strength-team-header">{team.label}</div>
                        <div className="mc-strength-rows">
                          {rows.map((r) => (
                            <div className="mc-strength-row" key={r.label}>
                              <span className="mc-strength-label">{r.label}</span>
                              <div className="mc-strength-bar-wrap">
                                <div
                                  className={`mc-strength-bar mc-strength-${r.cls}`}
                                  style={{ width: `${(r.value / maxVal) * 100}%` }}
                                />
                              </div>
                              <span className="mc-strength-val">{r.value}</span>
                            </div>
                          ))}
                          <div className="mc-strength-total-row">
                            <span className="mc-strength-label">Total</span>
                            <span className="mc-strength-total-val">{d.total}</span>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Attendance Breakdown */}
            {matchEnded && result?.attendance > 0 && (
              <div className="mc-attendance-section">
                <h3 className="mc-section-title">🏟️ Attendance — {result.attendance.toLocaleString()}</h3>
                {result.attendanceBreakdown ? (() => {
                  const bd = result.attendanceBreakdown;
                  const categories = [
                    { label: 'Premium', att: bd.premiumAtt, cap: bd.premiumCap, color: '#f59e0b' },
                    { label: 'Standard', att: bd.standardAtt, cap: bd.standardCap, color: '#22d3ee' },
                    { label: 'Economy', att: bd.economyAtt, cap: bd.economyCap, color: '#34d399' },
                    { label: 'Standing', att: bd.standingAtt, cap: bd.standingCap, color: '#94a3b8' },
                  ];
                  const totalCap = categories.reduce((s, c) => s + c.cap, 0);
                  const fillPct = totalCap > 0 ? ((result.attendance / totalCap) * 100).toFixed(1) : 0;
                  return (
                    <div className="mc-att-breakdown">
                      <div className="mc-att-overall">
                        <span className="mc-att-fill">{fillPct}% filled</span>
                        <span className="mc-att-cap">Capacity: {totalCap.toLocaleString()}</span>
                      </div>
                      <div className="mc-att-categories">
                        {categories.map(c => {
                          const pct = c.cap > 0 ? ((c.att / c.cap) * 100).toFixed(0) : 0;
                          return (
                            <div className="mc-att-cat" key={c.label}>
                              <div className="mc-att-cat-header">
                                <span className="mc-att-cat-label">{c.label}</span>
                                <span className="mc-att-cat-nums">{c.att.toLocaleString()} / {c.cap.toLocaleString()}</span>
                              </div>
                              <div className="mc-att-bar-wrap">
                                <div className="mc-att-bar" style={{ width: `${pct}%`, background: c.color }} />
                              </div>
                              <span className="mc-att-pct">{pct}%</span>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })() : (
                  <div className="mc-att-total-only">{result.attendance.toLocaleString()} spectators</div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ═══ RIVALRY ═══ */}
        {activeTab === 'rivalry' && (
          <div className="mc-rivalry">
            {!rivalry ? (
              <div className="mc-empty">Loading rivalry data...</div>
            ) : rivalry.totalMatches === 0 ? (
              <div className="mc-empty">No previous matches between these teams.</div>
            ) : (
              <>
                <div className="mc-rivalry-header">
                  <h3>Head to Head</h3>
                  <span className="mc-rivalry-total">{rivalry.totalMatches} match{rivalry.totalMatches !== 1 ? 'es' : ''}</span>
                </div>
                <div className="mc-rivalry-stats">
                  <div className="mc-rivalry-stat">
                    <span className="mc-rivalry-num mc-team1">{rivalry.team1Wins}</span>
                    <span className="mc-rivalry-label">{result.homeTeamName}</span>
                  </div>
                  <div className="mc-rivalry-stat">
                    <span className="mc-rivalry-num mc-draw">{rivalry.draws + rivalry.ties}</span>
                    <span className="mc-rivalry-label">Draw/Tie</span>
                  </div>
                  <div className="mc-rivalry-stat">
                    <span className="mc-rivalry-num mc-team2">{rivalry.team2Wins}</span>
                    <span className="mc-rivalry-label">{result.awayTeamName}</span>
                  </div>
                </div>
                <h4 className="mc-rivalry-recent-title">Recent Matches</h4>
                <div className="mc-rivalry-list">
                  {rivalry.matches.slice(0, 10).map((m, i) => (
                    <div key={i} className="mc-rivalry-match mc-rivalry-match-link"
                      onClick={() => navigate(`/match/${m.fixtureId}/scorecard`)}>
                      <span className="mc-rivalry-date">{m.date}</span>
                      <span className="mc-rivalry-format">{m.format}</span>
                      <span className="mc-rivalry-summary">{m.summary}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

      </div>

      {/* ─── Match Ended Overlay (live route only, dismissable) ─── */}
      {isLiveRoute && matchEnded && !overlayDismissed && (
        <div className="mc-overlay">
          <div className="mc-overlay-card">
            <HiOutlineTrophy className="mc-overlay-icon" />
            <h2>{result.summary}</h2>
            {result.manOfMatch && <p className="mc-overlay-motm">Player of the Match: {result.manOfMatch}</p>}
            <div className="mc-overlay-scores">
              {liveScorecard.map((rInn, i) => (
                <span key={i} className="mc-overlay-score-item">{rInn.battingTeam}: {rInn.scoreDisplay}</span>
              ))}
            </div>
            <div className="mc-overlay-actions">
              <button onClick={() => { setOverlayDismissed(true); setActiveTab('scorecard'); }}>
                <HiOutlineTrophy /> View Full Scorecard
              </button>
              <button onClick={() => { setOverlayDismissed(true); setActiveTab('commentary'); }}>
                <HiOutlineClipboardDocumentList /> View Commentary
              </button>
              <button className="mc-overlay-back" onClick={() => navigate('/challenges')}>
                Back to Challenges
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════
   SUB-COMPONENTS
   ════════════════════════════════════════════════════════════════ */

function ManhattanChart({ data, format }) {
  const [hovered, setHovered] = useState(null); // { inningsIdx, overNum, x, y, runs, wkts }

  if (!data || data.length === 0) return <div className="mc-empty">No data yet.</div>;
  const COLORS = ['#22d3ee', '#a78bfa', '#34d399', '#fb923c'];
  const ORDINAL = ['1st', '2nd', '3rd', '4th'];
  const sharedMaxRun = Math.max(...data.flatMap(d => d.runsPerOver), 6);
  const sharedMaxOvers = format === 'T20' ? 20 : format === 'ODI' ? 50
    : Math.max(...data.map(d => d.runsPerOver.length), 1);

  return (
    <div className="mc-manhattan-group">
      {data.map((d, idx) => {
        const overs = d.runsPerOver.length;
        const maxOvers = sharedMaxOvers;
        const maxRun = sharedMaxRun;
        const H = 180, PAD = 30;
        const W = format === 'FC' ? Math.max(600, maxOvers * 12 + PAD * 2) : 600;
        const barW = (W - PAD * 2) / maxOvers;
        const scaleY = (v) => H - PAD - (v / maxRun) * (H - PAD * 2);
        const labelStep = format === 'T20' ? 2 : maxOvers > 80 ? 10 : 5;
        const isScrollable = W > 600;
        const color = COLORS[idx % COLORS.length];
        const totalRuns = d.cumulative?.[overs - 1] ?? d.runsPerOver.reduce((a, b) => a + b, 0);
        const totalWkts = d.wicketsPerOver?.reduce((a, b) => a + b, 0) ?? 0;
        const label = data.length > 2
          ? `${d.battingTeam} — ${ORDINAL[d.inningsIdx] || ''} Innings`
          : `${d.battingTeam}`;
        const hovHere = hovered?.inningsIdx === d.inningsIdx ? hovered : null;

        return (
          <div key={idx} className="mc-chart-wrap">
            <div className="mc-manhattan-header">
              <span className="mc-manhattan-team" style={{ color }}>{label}</span>
              <span className="mc-manhattan-summary">{totalRuns}/{totalWkts} ({overs} ov)</span>
            </div>
            <div className={isScrollable ? 'mc-chart-scroll' : undefined}>
              <svg viewBox={`0 0 ${W} ${H}`} className="mc-chart"
                style={isScrollable ? { width: W, maxWidth: 'none' } : undefined}
                onMouseLeave={() => setHovered(null)}>
                {[0, Math.ceil(maxRun / 4), Math.ceil(maxRun / 2), Math.ceil(maxRun * 3 / 4), maxRun].map((v) => (
                  <g key={v}>
                    <line x1={PAD} y1={scaleY(v)} x2={W - PAD} y2={scaleY(v)} stroke="#1e293b" strokeWidth="0.5" />
                    <text x={PAD - 4} y={scaleY(v) + 4} fill="#64748b" fontSize="8" textAnchor="end">{v}</text>
                  </g>
                ))}
                {d.runsPerOver.map((r, o) => {
                  const bw = barW * 0.7;
                  const x = PAD + o * barW + barW * 0.15;
                  const wkts = d.wicketsPerOver?.[o] || 0;
                  const isHov = hovHere?.overNum === o;
                  return (
                    <g key={o}
                      onMouseEnter={() => setHovered({ inningsIdx: d.inningsIdx, overNum: o, x: x + bw / 2, y: scaleY(r), runs: r, wkts })}
                      style={{ cursor: 'default' }}>
                      <rect x={x} y={scaleY(r)} width={bw}
                        height={H - PAD - scaleY(r)} fill={color}
                        opacity={isHov ? 1 : 0.75} rx={1} />
                      {wkts > 0 && (
                        <g>
                          <circle cx={x + bw / 2} cy={scaleY(r) - 8} r={5} fill="#ef4444" opacity={0.9} />
                          <text x={x + bw / 2} y={scaleY(r) - 4.5} fill="#fff" fontSize="7" fontWeight="700" textAnchor="middle">{wkts}</text>
                        </g>
                      )}
                      {/* invisible hit area for small bars */}
                      <rect x={x} y={PAD} width={bw} height={H - PAD * 2} fill="transparent" />
                    </g>
                  );
                })}
                {Array.from({ length: maxOvers }, (_, i) => (
                  (i % labelStep === 0) && (
                    <text key={i} x={PAD + i * barW + barW / 2} y={H - 6} fill="#64748b" fontSize="7" textAnchor="middle">{i}</text>
                  )
                ))}
                {/* Hover tooltip */}
                {hovHere && (() => {
                  const wktText = hovHere.wkts > 0 ? `, ${hovHere.wkts} wkt${hovHere.wkts > 1 ? 's' : ''}` : '';
                  const text = `Over ${hovHere.overNum + 1}: ${hovHere.runs} runs${wktText}`;
                  const tw = text.length * 5.2 + 14;
                  const rawX = hovHere.x - tw / 2;
                  const tx = Math.min(Math.max(rawX, PAD), W - PAD - tw);
                  const ty = Math.max(hovHere.y - 24, PAD);
                  return (
                    <g style={{ pointerEvents: 'none' }}>
                      <rect x={tx} y={ty} width={tw} height={16} rx={3} fill="#0f172a" opacity={0.88} />
                      <text x={tx + tw / 2} y={ty + 10.5} fill="#f1f5f9" fontSize="8" textAnchor="middle">{text}</text>
                    </g>
                  );
                })()}
              </svg>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function WormChart({ data, format }) {
  if (!data || data.length === 0) return <div className="mc-empty">No data yet.</div>;
  const maxOvers = Math.max(...data.map((d) => d.cumulative.length), 1);
  const maxRuns = Math.max(...data.flatMap((d) => d.cumulative), 10);
  const H = 200, PAD = 30;
  const W = format === 'FC' ? Math.max(600, maxOvers * 8 + PAD * 2) : 600;
  const scaleX = (o) => PAD + (o / (maxOvers - 1 || 1)) * (W - PAD * 2);
  const scaleY = (v) => H - PAD - (v / maxRuns) * (H - PAD * 2);
  const COLORS = ['#22d3ee', '#a78bfa', '#34d399', '#fb923c'];
  const DASHES = ['none', '6,3', '2,2', '8,3,2,3'];
  const ORDINAL = ['1st', '2nd', '3rd', '4th'];
  const labelStep = format === 'T20' ? 2 : format === 'FC' ? (maxOvers > 80 ? 10 : 5) : 5;
  const isScrollable = format === 'FC' && W > 600;

  return (
    <div className="mc-chart-wrap">
      <div className={isScrollable ? 'mc-chart-scroll' : undefined}>
        <svg viewBox={`0 0 ${W} ${H}`} className="mc-chart" style={isScrollable ? { width: W, maxWidth: 'none' } : undefined}>
          {[0, Math.ceil(maxRuns / 4), Math.ceil(maxRuns / 2), Math.ceil(maxRuns * 3 / 4), maxRuns].map((v) => (
            <g key={v}>
              <line x1={PAD} y1={scaleY(v)} x2={W - PAD} y2={scaleY(v)} stroke="#1e293b" strokeWidth="0.5" />
              <text x={PAD - 4} y={scaleY(v) + 4} fill="#64748b" fontSize="8" textAnchor="end">{v}</text>
            </g>
          ))}
          {data.map((d, idx) => {
            if (d.cumulative.length === 0) return null;
            const points = d.cumulative.map((v, o) => `${scaleX(o)},${scaleY(v)}`).join(' ');
            return <polyline key={idx} points={points} fill="none" stroke={COLORS[idx % COLORS.length]} strokeWidth="1.5" strokeLinejoin="round" strokeDasharray={data.length > 2 ? DASHES[idx % DASHES.length] : 'none'} />;
          })}
          {Array.from({ length: maxOvers }, (_, i) => (
            (i % labelStep === 0) && (
              <text key={i} x={scaleX(i)} y={H - 6} fill="#64748b" fontSize="7" textAnchor="middle">{i}</text>
            )
          ))}
        </svg>
      </div>
      <div className="mc-chart-legend">
        {data.map((d, i) => (
          <span key={i} className="mc-legend-item">
            <span className="mc-legend-dot" style={{ background: COLORS[i % COLORS.length] }} />
            {d.battingTeam}{format === 'FC' && data.length > 2 ? ` (${ORDINAL[d.inningsIdx] || ''})` : ''}
          </span>
        ))}
      </div>
    </div>
  );
}

function RunRateChart({ data, format }) {
  if (!data || data.length === 0) return <div className="mc-empty">No data yet.</div>;
  const maxOvers = Math.max(...data.map((d) => d.runRatePerOver.length), 1);
  const maxRR = Math.max(...data.flatMap((d) => d.runRatePerOver), 6);
  const H = 200, PAD = 30;
  const W = format === 'FC' ? Math.max(600, maxOvers * 8 + PAD * 2) : 600;
  const scaleX = (o) => PAD + (o / (maxOvers - 1 || 1)) * (W - PAD * 2);
  const scaleY = (v) => H - PAD - (v / maxRR) * (H - PAD * 2);
  const COLORS = ['#22d3ee', '#a78bfa', '#34d399', '#fb923c'];
  const DASHES = ['none', '6,3', '2,2', '8,3,2,3'];
  const ORDINAL = ['1st', '2nd', '3rd', '4th'];
  const labelStep = format === 'T20' ? 2 : format === 'FC' ? (maxOvers > 80 ? 10 : 5) : 5;
  const isScrollable = format === 'FC' && W > 600;
  const showDots = maxOvers <= 60;

  return (
    <div className="mc-chart-wrap">
      <div className={isScrollable ? 'mc-chart-scroll' : undefined}>
        <svg viewBox={`0 0 ${W} ${H}`} className="mc-chart" style={isScrollable ? { width: W, maxWidth: 'none' } : undefined}>
          {[0, (maxRR / 4).toFixed(1), (maxRR / 2).toFixed(1), (maxRR * 3 / 4).toFixed(1), maxRR.toFixed(1)].map((v, i) => (
            <g key={i}>
              <line x1={PAD} y1={scaleY(Number(v))} x2={W - PAD} y2={scaleY(Number(v))} stroke="#1e293b" strokeWidth="0.5" />
              <text x={PAD - 4} y={scaleY(Number(v)) + 4} fill="#64748b" fontSize="8" textAnchor="end">{v}</text>
            </g>
          ))}
          {data.map((d, idx) => {
            if (d.runRatePerOver.length === 0) return null;
            const points = d.runRatePerOver.map((v, o) => `${scaleX(o)},${scaleY(v)}`).join(' ');
            return (
              <g key={idx}>
                <polyline points={points} fill="none" stroke={COLORS[idx % COLORS.length]} strokeWidth="1.5" strokeLinejoin="round" strokeDasharray={DASHES[idx % DASHES.length]} />
                {showDots && d.runRatePerOver.map((v, o) => (
                  <circle key={o} cx={scaleX(o)} cy={scaleY(v)} r={2.5} fill={COLORS[idx % COLORS.length]} />
                ))}
              </g>
            );
          })}
          {Array.from({ length: maxOvers }, (_, i) => (
            (i % labelStep === 0) && (
              <text key={i} x={scaleX(i)} y={H - 6} fill="#64748b" fontSize="7" textAnchor="middle">{i}</text>
            )
          ))}
        </svg>
      </div>
      <div className="mc-chart-legend">
        {data.map((d, i) => (
          <span key={i} className="mc-legend-item">
            <span className="mc-legend-dot" style={{ background: COLORS[i % COLORS.length] }} />
            {d.battingTeam}{format === 'FC' && data.length > 2 ? ` (${ORDINAL[d.inningsIdx] || ''})` : ''}
          </span>
        ))}
      </div>
    </div>
  );
}

function ComparisonRow({ label, val1, val2 }) {
  const n1 = Number(val1) || 0;
  const n2 = Number(val2) || 0;
  const max = Math.max(n1, n2, 1);
  return (
    <div className="mc-comp-row">
      <span className={`mc-comp-val left ${n1 >= n2 ? 'mc-comp-lead' : ''}`}>{val1}</span>
      <div className="mc-comp-bar-wrap">
        <div className="mc-comp-bar left" style={{ width: `${(n1 / max) * 50}%` }} />
        <span className="mc-comp-label">{label}</span>
        <div className="mc-comp-bar right" style={{ width: `${(n2 / max) * 50}%` }} />
      </div>
      <span className={`mc-comp-val right ${n2 >= n1 ? 'mc-comp-lead' : ''}`}>{val2}</span>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════
   UTILITY FUNCTIONS
   ════════════════════════════════════════════════════════════════ */

function buildInningsStats(innData, balls) {
  const batMap = {};
  const batOrder = [];
  const dismissed = new Set();
  let extras = 0, extWides = 0, extNoBalls = 0, extByes = 0, extLegByes = 0;
  balls.forEach((b) => {
    if (!batMap[b.batsman]) { batMap[b.batsman] = { playerName: b.batsman, playerId: b.batsmanId, runs: 0, balls: 0, fours: 0, sixes: 0, dots: 0, dismissal: null, bowler: null, fielder: null, notOut: true }; batOrder.push(b.batsman); }
    const bm = batMap[b.batsman];
    bm.runs += (b.isWide || b.isBye || b.isLegBye) ? 0 : (b.isNoBall ? Math.max(0, b.runs - 1) : b.runs);
    if (!b.isWide && !b.isNoBall) bm.balls += 1;
    else if (b.isNoBall) bm.balls += 1;
    if (b.isBoundary && !b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) bm.fours += 1;
    if (b.isSix && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye) bm.sixes += 1;
    if (b.runs === 0 && !b.isWide && !b.isNoBall && !b.isBye && !b.isLegBye && !b.isWicket) bm.dots += 1;
    if (b.isWicket) { dismissed.add(b.batsman); bm.notOut = false; bm.dismissal = b.dismissalType || 'out'; bm.bowler = b.bowler; bm.fielder = b.fielder || null; }
    if (b.isWide) { extras += b.runs; extWides += b.runs; }
    if (b.isNoBall) { extras += b.runs; extNoBalls += b.runs; }
    if (b.isBye) { extras += b.runs; extByes += b.runs; }
    if (b.isLegBye) { extras += b.runs; extLegByes += b.runs; }
  });
  const battingCard = batOrder.map((name) => {
    const bm = batMap[name];
    return { ...bm, strikeRate: bm.balls > 0 ? (bm.runs / bm.balls) * 100 : 0 };
  });

  const bowlMap = {};
  const bowlOrder = [];
  balls.forEach((b) => {
    if (!bowlMap[b.bowler]) { bowlMap[b.bowler] = { playerName: b.bowler, playerId: b.bowlerId, legalBalls: 0, maidens: 0, runs: 0, wickets: 0, dots: 0, wides: 0, noBalls: 0 }; bowlOrder.push(b.bowler); }
    const bw = bowlMap[b.bowler];
    bw.runs += (b.isBye || b.isLegBye) ? 0 : b.runs;
    if (b.isWicket && b.dismissalType !== 'RUN_OUT') bw.wickets += 1;
    if (b.isWide) bw.wides += 1;
    if (b.isNoBall) bw.noBalls += 1;
    if (!b.isWide && !b.isNoBall) {
      bw.legalBalls += 1;
      if (b.runs === 0 && !b.isWicket) bw.dots += 1;
    }
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
  const declSuffix = innData.declared ? 'd' : '';
  const scoreDisplay = `${totalRuns}/${totalWkts}${declSuffix} (${oversDisplay(legal)} ov)`;

  return {
    inningsNumber: innData.inningsNumber ?? 1,
    battingTeam: innData.battingTeam,
    scoreDisplay, battingCard, bowlingCard, extras, extWides, extNoBalls, extByes, extLegByes,
  };
}

function computePartnerships(balls) {
  if (balls.length === 0) return [];
  const partnerships = [];
  let pRuns = 0, pBalls = 0, pExtras = 0;
  let currentBatsmen = new Set();
  let batRuns = {};
  let batBalls = {};
  const flush = () => {
    const bats = [...currentBatsmen];
    const b1 = bats[0] || '—', b2 = bats[1] || '—';
    partnerships.push({
      bat1: b1, bat2: b2, runs: pRuns, balls: pBalls, extras: pExtras,
      bat1Runs: batRuns[b1] || 0, bat1Balls: batBalls[b1] || 0,
      bat2Runs: batRuns[b2] || 0, bat2Balls: batBalls[b2] || 0,
    });
  };
  balls.forEach((b) => {
    currentBatsmen.add(b.batsman);
    if (!(b.batsman in batRuns)) { batRuns[b.batsman] = 0; batBalls[b.batsman] = 0; }
    pRuns += b.runs;
    if (b.isWide) {
      // Wide: all runs are extras, batter doesn't face
      pExtras += b.runs;
    } else if (b.isNoBall) {
      // No-ball: 1 penalty = extra, rest goes to batter
      pExtras += 1;
      batRuns[b.batsman] += Math.max(0, b.runs - 1);
      batBalls[b.batsman] += 1;
      pBalls += 1;
    } else if (b.isBye || b.isLegBye) {
      // Bye/Leg-bye: all runs are extras, batter just faced the ball
      pExtras += b.runs;
      batBalls[b.batsman] += 1;
      pBalls += 1;
    } else {
      // Normal delivery: batter gets all runs
      batRuns[b.batsman] += b.runs;
      batBalls[b.batsman] += 1;
      pBalls += 1;
    }
    if (b.isWicket) {
      flush();
      pRuns = 0; pBalls = 0; pExtras = 0;
      batRuns = {}; batBalls = {};
      currentBatsmen.delete(b.batsman);
      for (const name of currentBatsmen) { batRuns[name] = 0; batBalls[name] = 0; }
    }
  });
  if (pBalls > 0 || pRuns > 0) {
    flush();
  }
  return partnerships;
}

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
function ballResultClass(b) {
  if (b.isWicket) return 'mcr-wicket';
  if (b.isSix) return 'mcr-six';
  if (b.isBoundary) return 'mcr-four';
  if (b.isBoundary && !b.isBye && !b.isLegBye && !b.isWide && !b.isNoBall) return 'mcr-four';
  if (b.runs === 0 && !b.isWide && !b.isNoBall) return 'mcr-dot';
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
