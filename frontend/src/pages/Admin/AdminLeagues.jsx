import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  HiOutlineGlobeAlt,
  HiOutlinePlus,
  HiOutlineChevronDown,
  HiOutlineChevronRight,
  HiOutlineCpuChip,
  HiOutlineTrash,
} from 'react-icons/hi2';
import {
  getLeagueStats,
  getLeaguesByCountry,
  createLeague,
  generateBotTeams,
  getBotStats,
  deleteLeague,
} from '../../api/auth';
import { COUNTRIES } from '../../constants/countries';
import './AdminLeagues.css';

export default function AdminLeagues() {
  const navigate = useNavigate();
  const [stats, setStats] = useState({ totalCountries: 0, totalLeagues: 0, countries: [] });
  const [loading, setLoading] = useState(true);

  // Expanded country detail
  const [expandedCountry, setExpandedCountry] = useState(null);
  const [countryData, setCountryData] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedSeason, setSelectedSeason] = useState(null); // null = all seasons
  const [selectedFormat, setSelectedFormat] = useState('T20'); // default T20

  // Create league form
  const [createCountry, setCreateCountry] = useState('');
  const [createFormat, setCreateFormat] = useState('T20');
  const [createDivision, setCreateDivision] = useState(3);
  const [submitting, setSubmitting] = useState(false);

  // Bot teams
  const [botStats, setBotStats] = useState(null);
  const [generatingBots, setGeneratingBots] = useState(false);

  const loadStats = useCallback(async () => {
    try {
      const [leagueRes, botRes] = await Promise.all([getLeagueStats(), getBotStats()]);
      setStats(leagueRes.data);
      setBotStats(botRes.data);
    } catch {
      toast.error('Failed to load league stats');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadStats(); }, [loadStats]);

  const loadCountryData = async (country, format = 'T20', season = null) => {
    setDetailLoading(true);
    try {
      const res = await getLeaguesByCountry(country, format, season);
      setCountryData(res.data);
    } catch {
      toast.error('Failed to load country leagues');
    } finally {
      setDetailLoading(false);
    }
  };

  const toggleCountry = (country) => {
    if (expandedCountry === country) {
      setExpandedCountry(null);
      setCountryData(null);
      setSelectedSeason(null);
      setSelectedFormat('T20');
    } else {
      setExpandedCountry(country);
      setSelectedSeason(null);
      setSelectedFormat('T20');
      loadCountryData(country, 'T20');
    }
  };

  const handleFormatChange = (format) => {
    setSelectedFormat(format);
    if (expandedCountry) {
      loadCountryData(expandedCountry, format, selectedSeason);
    }
  };

  const handleSeasonChange = (season) => {
    const val = season === '' ? null : parseInt(season);
    setSelectedSeason(val);
    if (expandedCountry) {
      loadCountryData(expandedCountry, selectedFormat, val);
    }
  };

  const handleCreate = async () => {
    if (!createCountry.trim()) {
      toast.error('Select a country');
      return;
    }
    if (createDivision < 3) {
      toast.error('Divisions 1 & 2 are auto-created');
      return;
    }
    setSubmitting(true);
    try {
      const res = await createLeague(createCountry.trim(), createFormat, createDivision);
      toast.success(`League ${res.data.created} created for ${createCountry}`);
      await loadStats();
      if (expandedCountry?.toLowerCase() === createCountry.trim().toLowerCase()) {
        loadCountryData(expandedCountry, selectedFormat, selectedSeason);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || 'Failed to create league';
      toast.error(typeof msg === 'string' ? msg : 'Failed to create league');
    } finally {
      setSubmitting(false);
    }
  };

  const handleGenerateBots = async () => {
    setGeneratingBots(true);
    try {
      const res = await generateBotTeams();
      toast.success(`Created ${res.data.created} bot teams across 18 countries`);
      await loadStats();
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || 'Failed to generate bot teams';
      toast.error(typeof msg === 'string' ? msg : 'Failed to generate bot teams');
    } finally {
      setGeneratingBots(false);
    }
  };

  const handleDeleteLeague = async (id, label) => {
    if (!window.confirm(`Delete league ${label} (all 3 formats at this div/number)?\nHuman teams will be relocated. Bot teams will be freed.`)) {
      return;
    }
    try {
      const res = await deleteLeague(id);
      toast.success(`Deleted ${res.data.deleted} across ${res.data.formatsDeleted} formats. ${res.data.humanTeamsRelocated} human team(s) relocated.`);
      await loadStats();
      if (expandedCountry) {
        loadCountryData(expandedCountry, selectedFormat, selectedSeason);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || 'Failed to delete league';
      toast.error(typeof msg === 'string' ? msg : 'Failed to delete league');
    }
  };

  if (loading) {
    return (
      <div className="al-page">
        <div className="al-loading">Loading league data…</div>
      </div>
    );
  }

  return (
    <div className="al-page">
      {/* ── Header ── */}
      <div className="al-header">
        <div className="al-header-left">
          <HiOutlineGlobeAlt className="al-header-icon" />
          <div>
            <h1>Admin — Leagues</h1>
            <p>Div 1 (1 league) and Div 2 (2 leagues) are auto-created per country. Add Div 3+ leagues manually.</p>
          </div>
        </div>
      </div>

      {/* ── Stats ── */}
      <div className="al-stats">
        <div className="al-stat-card">
          <span className="al-stat-label">Countries</span>
          <span className="al-stat-value">{stats.totalCountries}</span>
        </div>
        <div className="al-stat-card">
          <span className="al-stat-label">Total Leagues</span>
          <span className="al-stat-value al-stat-green">{stats.totalLeagues}</span>
        </div>
        <div className="al-stat-card">
          <span className="al-stat-label">Bot Teams</span>
          <span className="al-stat-value al-stat-cyan">{botStats?.totalBotTeams ?? 0}</span>
        </div>
        <div className="al-stat-card">
          <span className="al-stat-label">Countries with Bots</span>
          <span className="al-stat-value">{botStats?.countriesWithBots ?? 0}</span>
        </div>
      </div>

      {/* ── Bot Teams ── */}
      <div className="al-card">
        <div className="al-card-head">
          <h2>Bot Teams</h2>
          <span className="al-badge">24 per country</span>
        </div>
        <p className="al-form-hint">
          Generates 8 bot teams per league (Div 1 × 1 + Div 2 × 2 = 24 per country, 432 total).
          Same teams play all 3 formats (T20, ODI, FC). Real users replace bots when they register.
        </p>
        <button
          className="al-btn-bot"
          onClick={handleGenerateBots}
          disabled={generatingBots || (botStats?.totalBotTeams >= 432)}
        >
          <HiOutlineCpuChip />
          {generatingBots ? 'Generating...' : botStats?.totalBotTeams >= 432 ? 'All Bot Teams Generated' : 'Generate Bot Teams'}
        </button>
      </div>

      {/* ── Create League Form ── */}
      <div className="al-card">
        <div className="al-card-head">
          <h2>Create League</h2>
          <span className="al-badge">Div 3+</span>
        </div>

        <div className="al-create-form">
          <div className="al-form-row">
            <div className="al-form-group">
              <label className="al-label">Country</label>
              <select
                className="al-select"
                value={createCountry}
                onChange={(e) => setCreateCountry(e.target.value)}
              >
                <option value="">Select a country</option>
                {COUNTRIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>

            <div className="al-form-group">
              <label className="al-label">Format</label>
              <select
                className="al-select al-select-format"
                value={createFormat}
                onChange={(e) => setCreateFormat(e.target.value)}
              >
                <option value="T20">T20</option>
                <option value="ODI">ODI</option>
                <option value="FC">First Class</option>
              </select>
            </div>

            <div className="al-form-group">
              <label className="al-label">Division</label>
              <input
                type="number"
                className="al-input"
                min={3}
                value={createDivision}
                onChange={(e) => setCreateDivision(Math.max(3, parseInt(e.target.value) || 3))}
              />
            </div>

            <button
              className="al-btn-primary"
              onClick={handleCreate}
              disabled={submitting}
            >
              <HiOutlinePlus /> {submitting ? 'Creating...' : 'Add Next League'}
            </button>
          </div>

          <p className="al-form-hint">
            Each click adds the next league in sequence (e.g., 3.1 → 3.2 → 3.3 → 3.4).
            Max leagues per division: Div 3 = 4, Div 4 = 8, Div 5 = 16, etc.
          </p>
        </div>
      </div>

      {/* ── Country List ── */}
      <div className="al-card">
        <div className="al-card-head">
          <h2>Leagues by Country</h2>
          <span className="al-badge">{stats.countries.length} countr{stats.countries.length !== 1 ? 'ies' : 'y'}</span>
        </div>

        {stats.countries.length === 0 ? (
          <div className="al-empty-msg">No leagues yet. They will be auto-created when the server starts.</div>
        ) : (
          <div className="al-country-list">
            {stats.countries.map((c) => (
              <div key={c.country} className="al-country-item">
                <button className="al-country-row" onClick={() => toggleCountry(c.country)}>
                  <span className="al-country-chevron">
                    {expandedCountry === c.country ? <HiOutlineChevronDown /> : <HiOutlineChevronRight />}
                  </span>
                  <span className="al-country-name">{c.country}</span>
                  <div className="al-country-meta">
                    <span className="al-tag">{c.totalLeagues} league{c.totalLeagues !== 1 ? 's' : ''}</span>
                    <span className="al-tag">Max Div {c.maxDivision}</span>
                  </div>
                </button>

                {expandedCountry === c.country && (
                  <div className="al-country-detail">
                    {detailLoading ? (
                      <div className="al-detail-loading">Loading…</div>
                    ) : countryData ? (
                      <>
                        {/* Format & Season Filters */}
                        <div className="al-detail-filters">
                          <div className="al-filter-group">
                            <span className="al-filter-label">Format:</span>
                            <div className="al-format-tabs">
                              {['T20', 'ODI', 'FC'].map((f) => (
                                <button
                                  key={f}
                                  className={`al-format-tab ${selectedFormat === f ? 'active' : ''}`}
                                  onClick={() => handleFormatChange(f)}
                                >
                                  {f}
                                </button>
                              ))}
                            </div>
                          </div>
                          {countryData.seasons && countryData.seasons.length > 0 && (
                            <div className="al-filter-group">
                              <span className="al-filter-label">Season:</span>
                              <select
                                className="al-season-select"
                                value={selectedSeason ?? ''}
                                onChange={(e) => handleSeasonChange(e.target.value)}
                              >
                                <option value="">All Seasons</option>
                                {countryData.seasons.map((s) => (
                                  <option key={s} value={s}>Season {s}</option>
                                ))}
                              </select>
                            </div>
                          )}
                        </div>
                        <div className="al-divisions">
                          {Object.entries(countryData.divisions).map(([div, leagues]) => (
                          <div key={div} className="al-division-block">
                            <h4 className="al-div-title">Division {div}</h4>
                            <div className="al-league-chips">
                              {leagues.map((l) => (
                                <span
                                  key={l.id || l.leagueId}
                                  className="al-league-chip al-league-chip-clickable"
                                  onClick={() => navigate(`/league/${l.id}`)}
                                >
                                  {l.format} {l.leagueId}
                                  <span className="al-league-season">S{l.season}</span>
                                  {parseInt(div) >= 3 && leagues.indexOf(l) === leagues.length - 1 && (
                                    <button
                                      className="al-league-delete-btn"
                                      title="Delete this league (all formats)"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleDeleteLeague(l.id, l.format + ' ' + l.leagueId);
                                      }}
                                    >
                                      <HiOutlineTrash />
                                    </button>
                                  )}
                                </span>
                              ))}
                            </div>
                          </div>
                        ))}
                        </div>
                      </>
                    ) : null}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
