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
          onClick={() => navigate(`/match/${fixtureId}/commentary`)}
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
