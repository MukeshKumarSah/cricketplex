import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getCommentary } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineClipboardDocumentList,
  HiOutlineChevronLeft,
  HiOutlineTrophy,
} from 'react-icons/hi2';
import './Commentary.css';

export default function Commentary() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeInnings, setActiveInnings] = useState(1);
  const [overFilter, setOverFilter] = useState('all');

  useEffect(() => {
    setLoading(true);
    getCommentary(fixtureId)
      .then((res) => {
        if (res.data.found) {
          setData(res.data);
        } else {
          toast.error('Commentary not found');
        }
      })
      .catch(() => toast.error('Failed to load commentary'))
      .finally(() => setLoading(false));
  }, [fixtureId]);

  if (loading) return <div className="comm-loading">Loading commentary...</div>;
  if (!data) return <div className="comm-loading">No commentary found.</div>;

  const innings = data.innings?.find((i) => i.inningsNumber === activeInnings);
  const events = innings?.ballEvents || [];

  // Group by over
  const overGroups = {};
  for (const e of events) {
    const key = e.over;
    if (!overGroups[key]) overGroups[key] = [];
    overGroups[key].push(e);
  }
  const overs = Object.keys(overGroups).map(Number).sort((a, b) => a - b);

  const filteredOvers =
    overFilter === 'all'
      ? overs
      : overFilter === 'wickets'
      ? overs.filter((o) => overGroups[o].some((e) => e.isWicket))
      : overFilter === 'boundaries'
      ? overs.filter((o) => overGroups[o].some((e) => e.isBoundary || e.isSix))
      : overs;

  return (
    <div className="commentary-page">
      {/* Header */}
      <div className="comm-header">
        <button className="comm-back" onClick={() => navigate(-1)}>
          <HiOutlineChevronLeft />
        </button>
        <HiOutlineClipboardDocumentList className="comm-header-icon" />
        <div>
          <h1>Ball-by-Ball Commentary</h1>
          <p className="comm-summary">{data.summary}</p>
        </div>
        <button
          className="comm-scorecard-btn"
          onClick={() => navigate(`/match/${fixtureId}/scorecard`)}
        >
          <HiOutlineTrophy /> Scorecard
        </button>
      </div>

      {/* Innings Tabs */}
      <div className="comm-tabs-row">
        <div className="comm-innings-tabs">
          {data.innings?.map((inn) => (
            <button
              key={inn.inningsNumber}
              className={`comm-inn-tab ${activeInnings === inn.inningsNumber ? 'active' : ''}`}
              onClick={() => { setActiveInnings(inn.inningsNumber); setOverFilter('all'); }}
            >
              {inn.battingTeam} — {inn.scoreDisplay}
            </button>
          ))}
        </div>

        <div className="comm-filters">
          {['all', 'wickets', 'boundaries'].map((f) => (
            <button
              key={f}
              className={`comm-filter ${overFilter === f ? 'active' : ''}`}
              onClick={() => setOverFilter(f)}
            >
              {f === 'all' ? 'All' : f === 'wickets' ? 'Wickets' : 'Boundaries'}
            </button>
          ))}
        </div>
      </div>

      {/* Commentary Feed */}
      <div className="comm-feed">
        {filteredOvers.length === 0 ? (
          <div className="comm-loading">No events match filter.</div>
        ) : (
          filteredOvers.map((overNum) => {
            const balls = overGroups[overNum];
            const overRuns = balls.reduce((s, b) => s + b.runs, 0);
            const overWickets = balls.filter((b) => b.isWicket).length;
            return (
              <div key={overNum} className="comm-over-group">
                <div className="comm-over-header">
                  <span className="comm-over-label">Over {overNum}</span>
                  <span className="comm-over-summary">
                    {overRuns} run{overRuns !== 1 ? 's' : ''}
                    {overWickets > 0 && `, ${overWickets} wkt${overWickets > 1 ? 's' : ''}`}
                  </span>
                  <div className="comm-over-balls">
                    {balls.map((b, i) => (
                      <span
                        key={i}
                        className={`comm-ball-chip ${
                          b.isWicket ? 'chip-wicket' :
                          b.isSix ? 'chip-six' :
                          b.isBoundary ? 'chip-four' :
                          b.runs === 0 ? 'chip-dot' :
                          b.isWide || b.isNoBall ? 'chip-extra' :
                          'chip-run'
                        }`}
                      >
                        {b.isWicket ? 'W' :
                         b.isWide ? 'Wd' :
                         b.isNoBall ? 'Nb' :
                         b.isSix ? '6' :
                         b.isBoundary ? '4' :
                         b.runs}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="comm-ball-list">
                  {balls.map((b, i) => (
                    <div
                      key={i}
                      className={`comm-ball-row ${
                        b.isWicket ? 'comm-wicket-row' :
                        b.isSix ? 'comm-six-row' :
                        b.isBoundary ? 'comm-four-row' : ''
                      }`}
                    >
                      <span className="comm-ball-num">{b.overBall}</span>
                      <span className="comm-ball-players">
                        {b.bowler} to {b.batsman}
                      </span>
                      <span className={`comm-ball-result ${
                        b.isWicket ? 'res-wicket' :
                        b.isSix ? 'res-six' :
                        b.isBoundary ? 'res-four' :
                        b.runs === 0 ? 'res-dot' : ''
                      }`}>
                        {b.isWicket ? 'OUT' :
                         b.isWide ? `${b.runs} wide` :
                         b.isNoBall ? `${b.runs} no-ball` :
                         b.isSix ? 'SIX' :
                         b.isBoundary ? 'FOUR' :
                         b.runs === 0 ? '•' :
                         `${b.runs} run${b.runs > 1 ? 's' : ''}`}
                      </span>
                      {b.commentary && (
                        <span className="comm-ball-text">{b.commentary}</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
