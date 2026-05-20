import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getMatchResult } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineTrophy,
  HiOutlineChevronLeft,
  HiOutlineClipboardDocumentList,
} from 'react-icons/hi2';
import './Scorecard.css';

export default function Scorecard() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeInnings, setActiveInnings] = useState(1);

  useEffect(() => {
    setLoading(true);
    getMatchResult(fixtureId)
      .then((res) => {
        if (res.data.found) {
          setData(res.data);
        } else {
          toast.error('Match result not found');
        }
      })
      .catch(() => toast.error('Failed to load match result'))
      .finally(() => setLoading(false));
  }, [fixtureId]);

  if (loading) return <div className="sc-loading">Loading scorecard...</div>;
  if (!data) return <div className="sc-loading">No result found for this match.</div>;

  const innings = data.innings?.find((i) => i.inningsNumber === activeInnings);

  // ── Build Man of the Match performance summary ──
  const motmStats = (() => {
    if (!data.manOfMatchId || !data.innings) return null;
    const id = data.manOfMatchId;
    let batRuns = 0, batBalls = 0, batFours = 0, batSixes = 0, batSR = 0, batInnings = 0, batNotOut = false;
    let bowlWickets = 0, bowlRuns = 0, bowlOvers = '', bowlEcon = 0, bowlInnings = 0, bowlMaidens = 0;
    let teamName = '';

    for (const inn of data.innings) {
      const bc = inn.battingCard?.find((b) => b.playerId === id);
      if (bc) {
        batRuns += bc.runs;
        batBalls += bc.balls;
        batFours += bc.fours;
        batSixes += bc.sixes;
        batInnings++;
        batNotOut = bc.notOut;
        if (!teamName) teamName = inn.battingTeam;
      }
      const bw = inn.bowlingCard?.find((b) => b.playerId === id);
      if (bw) {
        bowlWickets += bw.wickets;
        bowlRuns += bw.runs;
        bowlOvers = bw.overs;
        bowlEcon = bw.economy;
        bowlMaidens += bw.maidens;
        bowlInnings++;
      }
    }

    if (batBalls > 0) batSR = ((batRuns / batBalls) * 100).toFixed(1);

    return {
      name: data.manOfMatch,
      teamName,
      batting: batInnings > 0 ? { runs: batRuns, balls: batBalls, fours: batFours, sixes: batSixes, sr: batSR, notOut: batNotOut } : null,
      bowling: bowlInnings > 0 ? { wickets: bowlWickets, runs: bowlRuns, overs: bowlOvers, economy: bowlEcon, maidens: bowlMaidens } : null,
    };
  })();

  // ── Compute performance points for all players (same formula as MoM engine) ──
  const performancePoints = (() => {
    if (!data.innings) return [];
    const players = {}; // playerId -> { name, teamName, teamId, batPts, bowlPts, totalPts }

    for (const inn of data.innings) {
      for (const bc of inn.battingCard || []) {
        if (!players[bc.playerId]) {
          players[bc.playerId] = { id: bc.playerId, name: bc.playerName, teamName: inn.battingTeam, teamId: inn.battingTeamId, batPts: 0, bowlPts: 0 };
        }
        let pts = bc.runs * 1.0 + bc.fours * 1.5 + bc.sixes * 2.0;
        if (bc.runs >= 50) pts += 15;
        if (bc.runs >= 100) pts += 30;
        players[bc.playerId].batPts += pts;
      }
      for (const bc of inn.bowlingCard || []) {
        if (!players[bc.playerId]) {
          players[bc.playerId] = { id: bc.playerId, name: bc.playerName, teamName: inn.bowlingTeam, teamId: null, batPts: 0, bowlPts: 0 };
        }
        let pts = bc.wickets * 20.0 + bc.maidens * 5.0 + bc.dotBalls * 0.5;
        if (bc.wickets >= 3) pts += 15;
        if (bc.wickets >= 5) pts += 30;
        const oversNum = parseFloat(bc.overs);
        if (oversNum > 0 && bc.economy < 5.0) pts += 10;
        players[bc.playerId].bowlPts += pts;
      }
    }

    return Object.values(players)
      .map((p) => ({ ...p, totalPts: +(p.batPts + p.bowlPts).toFixed(1), batPts: +p.batPts.toFixed(1), bowlPts: +p.bowlPts.toFixed(1) }))
      .sort((a, b) => b.totalPts - a.totalPts);
  })();

  // Group performance points by team
  const homeTeamName = data.homeTeamName;
  const awayTeamName = data.awayTeamName;
  const homePoints = performancePoints.filter((p) => p.teamName === homeTeamName);
  const awayPoints = performancePoints.filter((p) => p.teamName === awayTeamName);

  return (
    <div className="scorecard-page">
      {/* Header */}
      <div className="sc-header">
        <button className="sc-back" onClick={() => navigate(-1)}>
          <HiOutlineChevronLeft />
        </button>
        <HiOutlineTrophy className="sc-header-icon" />
        <div>
          <h1>Match Scorecard</h1>
          <p className="sc-summary">{data.summary}</p>
        </div>
        <button
          className="sc-commentary-btn"
          onClick={() => navigate(`/match/${fixtureId}?tab=commentary`)}
        >
          <HiOutlineClipboardDocumentList /> Commentary
        </button>
      </div>

      {/* Match Info Bar */}
      <div className="sc-info-bar">
        <div className="sc-info-item">
          <span className="sc-info-label">Toss</span>
          <span className="sc-info-value">
            {data.tossWinner} elected to {data.tossDecision?.toLowerCase()}
          </span>
        </div>
        {data.manOfMatch && (
          <div className="sc-info-item">
            <span className="sc-info-label">Player of the Match</span>
            <span className="sc-info-value sc-motm">{data.manOfMatch}</span>
          </div>
        )}
        {data.winner && (
          <div className="sc-info-item">
            <span className="sc-info-label">Result</span>
            <span className="sc-info-value sc-winner">{data.summary}</span>
          </div>
        )}
      </div>

      {/* ── Man of the Match Card ── */}
      {motmStats && (
        <div className="sc-motm-card" onClick={() => navigate(`/player/${data.manOfMatchId}`)}>
          <div className="sc-motm-header">
            <span className="sc-motm-trophy">🏅</span>
            <div className="sc-motm-title">
              <span className="sc-motm-label">Player of the Match</span>
              <span className="sc-motm-name">{motmStats.name}</span>
              <span className="sc-motm-team">{motmStats.teamName}</span>
            </div>
          </div>
          <div className="sc-motm-stats">
            {motmStats.batting && (
              <div className="sc-motm-stat-group">
                <span className="sc-motm-stat-title">🏏 Batting</span>
                <div className="sc-motm-stat-row">
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.batting.runs}{motmStats.batting.notOut ? '*' : ''}</span>
                    <span className="sc-motm-stat-lbl">Runs</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.batting.balls}</span>
                    <span className="sc-motm-stat-lbl">Balls</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.batting.fours}</span>
                    <span className="sc-motm-stat-lbl">4s</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.batting.sixes}</span>
                    <span className="sc-motm-stat-lbl">6s</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.batting.sr}</span>
                    <span className="sc-motm-stat-lbl">SR</span>
                  </div>
                </div>
              </div>
            )}
            {motmStats.bowling && (
              <div className="sc-motm-stat-group">
                <span className="sc-motm-stat-title">🎯 Bowling</span>
                <div className="sc-motm-stat-row">
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.bowling.wickets}</span>
                    <span className="sc-motm-stat-lbl">Wkts</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.bowling.runs}</span>
                    <span className="sc-motm-stat-lbl">Runs</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.bowling.overs}</span>
                    <span className="sc-motm-stat-lbl">Overs</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.bowling.maidens}</span>
                    <span className="sc-motm-stat-lbl">Mdns</span>
                  </div>
                  <div className="sc-motm-stat">
                    <span className="sc-motm-stat-val">{motmStats.bowling.economy?.toFixed(1)}</span>
                    <span className="sc-motm-stat-lbl">Econ</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Innings Tabs */}
      <div className="sc-innings-tabs">
        {data.innings?.map((inn) => (
          <button
            key={inn.inningsNumber}
            className={`sc-inn-tab ${activeInnings === inn.inningsNumber ? 'active' : ''}`}
            onClick={() => setActiveInnings(inn.inningsNumber)}
          >
            <span className="sc-inn-team">{inn.battingTeam}</span>
            <span className="sc-inn-score">{inn.scoreDisplay}</span>
          </button>
        ))}
      </div>

      {innings && (
        <>
          {/* Batting Card */}
          <div className="sc-section">
            <h3 className="sc-section-title">Batting</h3>
            <div className="sc-table">
              <div className="sc-table-head">
                <span className="sc-col-name">Batter</span>
                <span className="sc-col-dismissal">Dismissal</span>
                <span className="sc-col-num">R</span>
                <span className="sc-col-num">B</span>
                <span className="sc-col-num">4s</span>
                <span className="sc-col-num">6s</span>
                <span className="sc-col-num">SR</span>
              </div>
              {innings.battingCard?.map((bc, idx) => (
                <div key={idx} className={`sc-table-row ${bc.notOut ? 'sc-not-out' : ''}`}>
                  <span className="sc-col-name">
                    <span className="sc-player-name">{bc.playerName}</span>
                    {bc.notOut && <span className="sc-not-out-badge">*</span>}
                  </span>
                  <span className="sc-col-dismissal sc-dismissal-text">
                    {bc.notOut
                      ? 'not out'
                      : formatDismissal(bc)}
                  </span>
                  <span className={`sc-col-num ${bc.runs >= 50 ? 'sc-milestone' : ''}`}>{bc.runs}</span>
                  <span className="sc-col-num">{bc.balls}</span>
                  <span className="sc-col-num">{bc.fours}</span>
                  <span className="sc-col-num">{bc.sixes}</span>
                  <span className="sc-col-num">{bc.strikeRate?.toFixed(1)}</span>
                </div>
              ))}
              <div className="sc-table-footer">
                <span>Extras: {innings.extras}</span>
                <span className="sc-total">
                  Total: {innings.totalRuns}/{innings.totalWickets} ({innings.scoreDisplay?.split('(')[1]}
                </span>
              </div>
            </div>
          </div>

          {/* Bowling Card */}
          <div className="sc-section">
            <h3 className="sc-section-title">Bowling</h3>
            <div className="sc-table">
              <div className="sc-table-head">
                <span className="sc-col-name">Bowler</span>
                <span className="sc-col-num">O</span>
                <span className="sc-col-num">M</span>
                <span className="sc-col-num">R</span>
                <span className="sc-col-num">W</span>
                <span className="sc-col-num">Econ</span>
                <span className="sc-col-num">Dots</span>
              </div>
              {innings.bowlingCard?.map((bc, idx) => (
                <div key={idx} className={`sc-table-row ${bc.wickets >= 3 ? 'sc-wicket-haul' : ''}`}>
                  <span className="sc-col-name">{bc.playerName}</span>
                  <span className="sc-col-num">{bc.overs}</span>
                  <span className="sc-col-num">{bc.maidens}</span>
                  <span className="sc-col-num">{bc.runs}</span>
                  <span className={`sc-col-num ${bc.wickets >= 3 ? 'sc-milestone' : ''}`}>{bc.wickets}</span>
                  <span className="sc-col-num">{bc.economy?.toFixed(1)}</span>
                  <span className="sc-col-num">{bc.dotBalls}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* ── Performance Points ── */}
      {performancePoints.length > 0 && (
        <div className="sc-perf-section">
          <h3 className="sc-section-title">⭐ Performance Points</h3>
          <div className="sc-perf-teams">
            {[{ label: homeTeamName, players: homePoints }, { label: awayTeamName, players: awayPoints }].map((team) => (
              <div className="sc-perf-team" key={team.label}>
                <div className="sc-perf-team-header">{team.label}</div>
                <div className="sc-perf-table">
                  <div className="sc-perf-head">
                    <span className="sc-perf-col-rank">#</span>
                    <span className="sc-perf-col-name">Player</span>
                    <span className="sc-perf-col-num">Bat</span>
                    <span className="sc-perf-col-num">Bowl</span>
                    <span className="sc-perf-col-total">Total</span>
                  </div>
                  {team.players.map((p, i) => (
                    <div
                      key={p.id}
                      className={`sc-perf-row ${p.id === data.manOfMatchId ? 'sc-perf-motm' : ''}`}
                      onClick={() => navigate(`/player/${p.id}`)}
                    >
                      <span className="sc-perf-col-rank">{i + 1}</span>
                      <span className="sc-perf-col-name">
                        {p.name}
                        {p.id === data.manOfMatchId && <span className="sc-perf-motm-badge">MoM</span>}
                      </span>
                      <span className="sc-perf-col-num">{p.batPts}</span>
                      <span className="sc-perf-col-num">{p.bowlPts}</span>
                      <span className="sc-perf-col-total">{p.totalPts}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
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

