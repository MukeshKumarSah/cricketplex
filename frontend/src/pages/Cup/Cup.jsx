import { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getCupCurrent, getCupBySeason, getCupMyStatus, getTeamStats, getCupStats } from '../../api/auth';
import { HiOutlineTrophy, HiOutlineCalendarDays } from 'react-icons/hi2';
import './Cup.css';

const ROUND_NAMES = [
  '', // index 0 unused
  'Round of 4096', 'Round of 2048', 'Round of 1024',
  'Round of 512', 'Round of 256', 'Round of 128',
  'Round of 64', 'Quarter-Final', 'Semi-Final',
  'Final', 'Grand Final', 'Super Final',
];

function getRoundName(round, totalRounds) {
  const remaining = totalRounds - round + 1;
  if (remaining === 1) return 'Final';
  if (remaining === 2) return 'Semi-Final';
  if (remaining === 3) return 'Quarter-Final';
  const teams = Math.pow(2, remaining);
  return `Round of ${teams}`;
}

function fmtDate(d) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC',
  });
}

function fmtCoins(n) {
  if (n == null || n === 0) return '—';
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000)     return `${(n / 1_000).toFixed(0)}K`;
  return String(n);
}

export default function Cup() {
  const [cup, setCup]         = useState(null);
  const [myStatus, setMyStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);
  const [tab, setTab]         = useState('bracket'); // 'bracket' | 'seeds' | 'my-stats' | 'cup-stats'
  const [season, setSeason]   = useState(null);
  const [selectedRound, setSelectedRound] = useState(null); // null = 'all'
  const [statsData, setStatsData]       = useState(null);
  const [cupStatsData, setCupStatsData] = useState(null);
  const [statsTab, setStatsTab]         = useState('batting'); // batting | bowling | fielding
  const [statsLoading, setStatsLoading] = useState(false);
  const [cupStatsLoading, setCupStatsLoading] = useState(false);
  const [statsLoadedSeason, setStatsLoadedSeason]       = useState(null); // cache key
  const [cupStatsLoadedSeason, setCupStatsLoadedSeason] = useState(null); // cache key
  const navigate = useNavigate();

  const loadCup = useCallback(async (s) => {
    setLoading(true);
    setError(null);
    try {
      const res = s
        ? await getCupBySeason(s)
        : await getCupCurrent();
      setCup(res.data);
      if (res.data?.season) setSeason(res.data.season);
      // Auto-select the highest active/played round
      const r = res.data?.rounds || {};
      const allR = Object.keys(r).map(Number).sort((a, b) => a - b);
      const liveR = allR.filter(n => (r[n] || []).some(m => m.status === 'IN_PROGRESS'));
      const doneR = allR.filter(n => (r[n] || []).some(m => m.status === 'COMPLETED'));
      const best  = liveR.length ? Math.max(...liveR)
                  : doneR.length ? Math.max(...doneR)
                  : allR[0] ?? null;
      setSelectedRound(best ?? null);
    } catch {
      setError('Failed to load cup data.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadCup(null); }, [loadCup]);

  useEffect(() => {
    getCupMyStatus()
      .then(r => setMyStatus(r.data))
      .catch(() => {});
  }, []);

  // Load MY stats — skip if already loaded for this season
  useEffect(() => {
    if (tab !== 'my-stats' || !season) return;
    if (statsLoadedSeason === season) return; // cached
    setStatsLoading(true);
    const fmt = cup?.format || 'T20';
    getTeamStats(fmt, 'CUP', season)
      .then(r => { setStatsData(r.data); setStatsLoadedSeason(season); })
      .catch(() => setStatsData(null))
      .finally(() => setStatsLoading(false));
  }, [tab, season, cup?.format]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load CUP-WIDE stats — skip if already loaded for this season
  useEffect(() => {
    if (tab !== 'cup-stats' || !season) return;
    if (cupStatsLoadedSeason === season) return; // cached
    setCupStatsLoading(true);
    getCupStats(season)
      .then(r => { setCupStatsData(r.data); setCupStatsLoadedSeason(season); })
      .catch(() => setCupStatsData(null))
      .finally(() => setCupStatsLoading(false));
  }, [tab, season]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <div className="cup-loading">Loading Cup…</div>;
  if (error)   return <div className="cup-loading">{error}</div>;

  if (!cup?.exists) {
    return (
      <div className="cup-page">
        <div className="cup-empty">
          <div className="cup-empty-icon">🏆</div>
          <h3>No Cup Yet This Season</h3>
          <p>The Cup bracket will be drawn on Day 1 of the season.</p>
        </div>
      </div>
    );
  }

  const { format, status, bracketSize, totalRounds, currentRound, startDate, rounds } = cup;
  const roundNumbers = Object.keys(rounds || {}).map(Number).sort((a, b) => a - b);

  // Determine my team ID from myStatus
  const myTeamId = myStatus?.status?.teamId ?? null;

  return (
    <div className="cup-page">
      {/* ── Header ── */}
      <div className="cup-header">
        <HiOutlineTrophy style={{ fontSize: '1.6rem', color: 'var(--accent)' }} />
        <h1 className="cup-title">Season {season} Cup</h1>
        <span className="cup-format-pill">{format}</span>
        <span className={`cup-badge ${status}`}>{status}</span>

        <div className="cup-season-select">
          <label>Season:</label>
          <select value={season || ''} onChange={e => {
            setStatsLoadedSeason(null);
            setCupStatsLoadedSeason(null);
            loadCup(Number(e.target.value));
          }}>
            {Array.from({ length: season || 1 }, (_, i) => i + 1).map(s => (
              <option key={s} value={s}>Season {s}</option>
            ))}
          </select>
        </div>
      </div>

      {/* ── Meta ── */}
      <div className="cup-meta">
        <div className="cup-meta-item">
          <span className="cup-meta-label">Bracket Size</span>
          <span className="cup-meta-val">{bracketSize?.toLocaleString()} teams</span>
        </div>
        <div className="cup-meta-item">
          <span className="cup-meta-label">Total Rounds</span>
          <span className="cup-meta-val">{totalRounds}</span>
        </div>
        <div className="cup-meta-item">
          <span className="cup-meta-label">Current Round</span>
          <span className="cup-meta-val">
            {status === 'COMPLETED' ? 'Complete' : (currentRound > 0 ? `R${currentRound} – ${getRoundName(currentRound, totalRounds)}` : 'Not started')}
          </span>
        </div>
        <div className="cup-meta-item">
          <span className="cup-meta-label">Start Date</span>
          <span className="cup-meta-val">{fmtDate(startDate)}</span>
        </div>
        <div className="cup-meta-item">
          <span className="cup-meta-label">Match Time</span>
          <span className="cup-meta-val">07:00 UTC</span>
        </div>
      </div>

      {/* ── My Status ── */}
      {myStatus?.inCup && myStatus.status && (() => {
        const s = myStatus.status;
        const isChampion = !s.eliminatedRound && s.cupStatus === 'COMPLETED';
        const alive = !s.eliminatedRound && s.cupStatus !== 'COMPLETED';
        return (
          <div className="cup-my-status">
            <div>
              <h3>My Cup Status</h3>
            </div>
            <div className="cup-status-item">
              <span className="cup-status-value">#{s.seedRank}</span>
              <span className="cup-status-label">Seed</span>
            </div>
            <div className="cup-status-item">
              <span className="cup-status-value">
                {isChampion ? '🏆 Champion' : s.eliminatedRound ? `R${s.eliminatedRound}` : `Alive (R${s.currentRound})`}
              </span>
              <span className="cup-status-label">Round Reached</span>
            </div>
            <div className="cup-status-item">
              <span className="cup-status-value cup-prize-tag">{fmtCoins(s.prizeWon)} coins</span>
              <span className="cup-status-label">Prize Earned</span>
            </div>
            <span className={`cup-alive-tag ${isChampion ? 'champion' : alive ? 'alive' : 'eliminated'}`}>
              {isChampion ? '🏆 Champion' : alive ? 'Still In' : 'Eliminated'}
            </span>
          </div>
        );
      })()}

      {/* ── Tabs ── */}
      <div className="cup-tabs">
        <button className={`cup-tab ${tab === 'bracket' ? 'active' : ''}`} onClick={() => setTab('bracket')}>Bracket</button>
        <button className={`cup-tab ${tab === 'seeds' ? 'active' : ''}`} onClick={() => setTab('seeds')}>Seeds / Teams</button>
        <button className={`cup-tab ${tab === 'cup-stats' ? 'active' : ''}`} onClick={() => setTab('cup-stats')}>Cup Stats</button>
        <button className={`cup-tab ${tab === 'my-stats' ? 'active' : ''}`} onClick={() => setTab('my-stats')}>My Stats</button>
      </div>

      {/* ── Bracket Tab ── */}
      {tab === 'bracket' && (
        <div className="cup-rounds">
          {/* Round filter */}
          <div className="cup-round-filter">
            <label>Round:</label>
            <div className="cup-round-filter-pills">
              <button
                className={`cup-round-pill ${selectedRound === null ? 'active' : ''}`}
                onClick={() => setSelectedRound(null)}
              >All</button>
              {roundNumbers.map(r => (
                <button
                  key={r}
                  className={`cup-round-pill ${selectedRound === r ? 'active' : ''}`}
                  onClick={() => setSelectedRound(r)}
                >
                  {getRoundName(r, totalRounds)}
                </button>
              ))}
            </div>
          </div>

          {(selectedRound === null ? roundNumbers : roundNumbers.filter(r => r === selectedRound)).map(r => {
            const matches = rounds[r] || [];
            const isCurrent = r === currentRound && status === 'ONGOING';
            return (
              <div key={r} className={`cup-round-section ${isCurrent ? 'current-round' : ''}`}>
                <h3>
                  Round {r} — {getRoundName(r, totalRounds)}
                  {isCurrent && ' (Live)'}
                </h3>
                <div className="cup-matches-grid">
                  {matches.map(m => {
                    const homeId  = m.homeTeam?.id;
                    const awayId  = m.awayTeam?.id;
                    const winnerId = m.winner?.id;
                    const rawStatus = (m.status || '').toUpperCase();
                    const st = rawStatus; // SCHEDULED | IN_PROGRESS | COMPLETED
                    const cardClass = st === 'IN_PROGRESS' ? 'live'
                                    : st === 'COMPLETED'   ? 'completed'
                                    : 'scheduled';

                    // Destination URL depends on match state
                    const matchLink = st === 'COMPLETED'   ? `/match/${m.fixtureId}/scorecard`
                                    : st === 'IN_PROGRESS' ? `/match/${m.fixtureId}/live`
                                    : `/match/${m.fixtureId}/preview`;

                    return (
                      <Link
                        key={m.fixtureId}
                        to={matchLink}
                        className={`cup-match-card ${cardClass}`}
                        style={{ textDecoration: 'none', display: 'block' }}
                      >
                        <div className="cup-match-teams">
                          <span className={`cup-team-name ${st === 'COMPLETED' && winnerId === homeId ? 'winner' : st === 'COMPLETED' && winnerId && winnerId !== homeId ? 'loser' : ''}`}>
                            {m.homeTeam?.name}
                          </span>
                          <span className="cup-vs">vs</span>
                          <span className={`cup-team-name ${st === 'COMPLETED' && winnerId === awayId ? 'winner' : st === 'COMPLETED' && winnerId && winnerId !== awayId ? 'loser' : ''}`}>
                            {m.awayTeam?.name}
                          </span>
                        </div>
                        <div className="cup-match-meta">
                          <span className="cup-match-date">
                            <HiOutlineCalendarDays /> {fmtDate(m.matchDate)}
                          </span>
                          <span className={`cup-match-status-tag ${st === 'IN_PROGRESS' ? 'IN_PROGRESS' : st}`}>
                            {st === 'IN_PROGRESS' ? '🔴 Live' : st === 'COMPLETED' ? 'Done' : 'Upcoming'}
                          </span>
                        </div>
                        <span className="cup-match-cta">
                          {st === 'COMPLETED'   ? 'View Scorecard →'
                          : st === 'IN_PROGRESS' ? 'Watch Live →'
                          : 'Preview →'}
                        </span>
                      </Link>
                    );
                  })}
                </div>
              </div>
            );
          })}
          {roundNumbers.length === 0 && (
            <p style={{ color: 'var(--text-3)', fontSize: '0.9rem' }}>Fixtures will appear once the Cup begins.</p>
          )}
          {roundNumbers.length > 0 && selectedRound !== null &&
            !(rounds[selectedRound]?.length) && (
            <p style={{ color: 'var(--text-3)', fontSize: '0.9rem' }}>No fixtures for this round yet.</p>
          )}
        </div>
      )}
      {/* ── Seeds Tab ── */}
      {tab === 'seeds' && (
        <div className="cup-seeds-table-wrap">
          <table className="cup-seeds-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Team</th>
                <th>Country</th>
                <th>Type</th>
                <th>Status</th>
                <th>Prize</th>
              </tr>
            </thead>
            <tbody>
              {(cup.teams || []).map(t => {
                const isElim = t.eliminatedRound != null;
                const isChampion = !isElim && status === 'COMPLETED';
                return (
                  <tr key={t.teamId}>
                    <td>{t.seedRank}</td>
                    <td>
                      <Link to={`/team/${t.teamId}`} style={{ color: 'var(--accent)', textDecoration: 'none' }}>
                        {t.teamName}
                      </Link>
                    </td>
                    <td>{t.country}</td>
                    <td>{t.isBot ? 'Bot' : 'Human'}</td>
                    <td className={isChampion ? 'seed-alive' : isElim ? 'seed-out' : 'seed-alive'}>
                      {isChampion ? '🏆 Champion' : isElim ? `Out R${t.eliminatedRound}` : 'In'}
                    </td>
                    <td className={t.prizeWon > 0 ? 'cup-prize-tag' : ''}>
                      {fmtCoins(t.prizeWon)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Cup Stats Tab (all players) ── */}
      {tab === 'cup-stats' && (
        <CupStatsPanel
          data={cupStatsData}
          loading={cupStatsLoading}
          season={season}
          statsTab={statsTab}
          setStatsTab={setStatsTab}
          navigate={navigate}
          showTeam
        />
      )}

      {/* ── My Stats Tab ── */}
      {tab === 'my-stats' && (
        <CupStatsPanel
          data={statsData}
          loading={statsLoading}
          season={season}
          statsTab={statsTab}
          setStatsTab={setStatsTab}
          navigate={navigate}
          showTeam={false}
        />
      )}
    </div>
  );
}

// ── Reusable stats panel ────────────────────────────────────────────────────
function CupStatsPanel({ data, loading, season, statsTab, setStatsTab, navigate, showTeam }) {
  if (loading) return <div className="cup-loading">Loading stats…</div>;
  if (!data) return (
    <div className="cup-empty">
      <p>No Cup stats available yet for Season {season}.</p>
    </div>
  );

  const tabs = ['batting', 'bowling', 'fielding'];

  return (
    <div className="cup-stats-section">
      <div className="cup-stats-tabs">
        {tabs.map(k => (
          <button
            key={k}
            className={`cup-stats-tab ${statsTab === k ? 'active' : ''}`}
            onClick={() => setStatsTab(k)}
          >{k.charAt(0).toUpperCase() + k.slice(1)}</button>
        ))}
      </div>

      {statsTab === 'batting' && (
        data.batting?.length
          ? <div className="cup-stats-table-wrap"><table className="cup-stats-table">
              <thead><tr>
                <th className="cup-stats-player-col">Player</th>
                {showTeam && <th>Team</th>}
                <th>Mat</th><th>Inn</th><th>NO</th>
                <th className="cup-hi-col">Runs</th><th>HS</th>
                <th>Avg</th><th>SR</th><th>100s</th><th>50s</th><th>4s</th><th>6s</th>
              </tr></thead>
              <tbody>{data.batting.map(r => (
                <tr key={r.id} className="cup-stats-row" onClick={() => navigate(`/player/${r.id}`)}>
                  <td>
                    <div className="cup-stats-player">
                      <span className="cup-stats-name">{r.name}</span>
                      <span className="cup-stats-role">{roleShort(r.role)}</span>
                    </div>
                  </td>
                  {showTeam && <td className="cup-stats-team">{r.teamName}</td>}
                  <td>{r.matches}</td><td>{r.innings}</td><td>{r.notOuts}</td>
                  <td className="cup-hi-col">{r.runs}</td><td>{r.highest}</td>
                  <td>{r.average}</td><td>{r.strikeRate}</td>
                  <td>{r.hundreds}</td><td>{r.fifties}</td><td>{r.fours}</td><td>{r.sixes}</td>
                </tr>
              ))}</tbody>
            </table></div>
          : <div className="cup-empty"><p>No batting data yet.</p></div>
      )}

      {statsTab === 'bowling' && (
        data.bowling?.length
          ? <div className="cup-stats-table-wrap"><table className="cup-stats-table">
              <thead><tr>
                <th className="cup-stats-player-col">Player</th>
                {showTeam && <th>Team</th>}
                <th>Mat</th><th>Inn</th><th>Overs</th><th>Runs</th>
                <th className="cup-hi-col">Wkts</th>
                <th>Best</th><th>Avg</th><th>Econ</th><th>SR</th><th>Mdns</th><th>5W</th><th>3W</th>
              </tr></thead>
              <tbody>{data.bowling.map(r => (
                <tr key={r.id} className="cup-stats-row" onClick={() => navigate(`/player/${r.id}`)}>
                  <td>
                    <div className="cup-stats-player">
                      <span className="cup-stats-name">{r.name}</span>
                      <span className="cup-stats-role">{roleShort(r.role)}</span>
                    </div>
                  </td>
                  {showTeam && <td className="cup-stats-team">{r.teamName}</td>}
                  <td>{r.matches}</td><td>{r.innings}</td><td>{r.overs}</td><td>{r.runs}</td>
                  <td className="cup-hi-col">{r.wickets}</td>
                  <td>{r.best}</td><td>{r.average}</td><td>{r.economy}</td>
                  <td>{r.strikeRate}</td><td>{r.maidens}</td><td>{r.fiveWickets}</td><td>{r.threeWickets}</td>
                </tr>
              ))}</tbody>
            </table></div>
          : <div className="cup-empty"><p>No bowling data yet.</p></div>
      )}

      {statsTab === 'fielding' && (
        data.fielding?.length
          ? <div className="cup-stats-table-wrap"><table className="cup-stats-table">
              <thead><tr>
                <th className="cup-stats-player-col">Player</th>
                {showTeam && <th>Team</th>}
                <th>Mat</th>
                <th className="cup-hi-col">Ct</th><th>St</th><th>RO</th>
                <th className="cup-hi-col">Total</th>
              </tr></thead>
              <tbody>{data.fielding.map(r => (
                <tr key={r.id} className="cup-stats-row" onClick={() => navigate(`/player/${r.id}`)}>
                  <td>
                    <div className="cup-stats-player">
                      <span className="cup-stats-name">{r.name}</span>
                      <span className="cup-stats-role">{roleShort(r.role)}</span>
                    </div>
                  </td>
                  {showTeam && <td className="cup-stats-team">{r.teamName}</td>}
                  <td>{r.matches}</td>
                  <td className="cup-hi-col">{r.catches}</td><td>{r.stumpings}</td><td>{r.runOuts}</td>
                  <td className="cup-hi-col">{r.total}</td>
                </tr>
              ))}</tbody>
            </table></div>
          : <div className="cup-empty"><p>No fielding data yet.</p></div>
      )}
    </div>
  );
}

function roleShort(role) {
  const map = { BATSMAN: 'BAT', BOWLER: 'BOWL', ALL_ROUNDER: 'AR', KEEPER: 'WK' };
  return map[role] || (role ? role.charAt(0) : '');
}

