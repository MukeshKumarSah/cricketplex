import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import {
  HiOutlineShieldCheck,
  HiOutlineTrash,
  HiOutlinePlus,
  HiOutlineChevronDown,
  HiOutlineChevronRight,
  HiOutlineUserPlus,
} from 'react-icons/hi2';
import {
  getPoolStats,
  getPoolByCountry,
  addFirstNames,
  addLastNames,
  deleteFirstName,
  deleteLastName,
  assignSquadToTeam,
  getTeamList,
} from '../../api/auth';
import { COUNTRIES } from '../../constants/countries';
import './AdminPlayers.css';

export default function AdminPlayers() {
  const [stats, setStats] = useState({ totalFirstNames: 0, totalLastNames: 0, totalCombinations: 0, countries: [] });
  const [loading, setLoading] = useState(true);

  // Add names form
  const [addCountry, setAddCountry] = useState('');
  const [firstInput, setFirstInput] = useState('');
  const [lastInput, setLastInput] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Expanded country detail
  const [expandedCountry, setExpandedCountry] = useState(null);
  const [countryPool, setCountryPool] = useState(null);
  const [poolLoading, setPoolLoading] = useState(false);

  // Team assignment
  const [teams, setTeams] = useState([]);
  const [teamsLoading, setTeamsLoading] = useState(false);
  const [assigning, setAssigning] = useState(false);

  const loadStats = useCallback(async () => {
    try {
      const res = await getPoolStats();
      setStats(res.data);
    } catch {
      toast.error('Failed to load pool stats');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadStats(); }, [loadStats]);

  const loadCountryPool = async (country) => {
    setPoolLoading(true);
    try {
      const res = await getPoolByCountry(country);
      setCountryPool(res.data);
    } catch {
      toast.error('Failed to load pool data');
    } finally {
      setPoolLoading(false);
    }
  };

  const toggleCountry = (country) => {
    if (expandedCountry === country) {
      setExpandedCountry(null);
      setCountryPool(null);
    } else {
      setExpandedCountry(country);
      loadCountryPool(country);
    }
  };

  // Capitalize first letter of each word
  const capitalize = (s) => s.replace(/\b\w/g, (c) => c.toUpperCase());

  // Parse comma/newline separated names, auto-capitalize
  const parseNames = (input) =>
    input.split(/[,\r\n]+/).map((n) => n.trim()).filter(Boolean).map(capitalize);

  const handleAddFirstNames = async () => {
    const names = parseNames(firstInput);
    if (!addCountry.trim()) { toast.error('Enter a country'); return; }
    if (names.length === 0) { toast.error('Enter at least one first name'); return; }
    setSubmitting(true);
    try {
      const res = await addFirstNames(addCountry.trim(), names);
      toast.success(`${res.data.added} first name(s) added` + (res.data.skipped ? `, ${res.data.skipped} duplicate(s) skipped` : ''));
      setFirstInput('');
      await loadStats();
      if (expandedCountry?.toLowerCase() === addCountry.trim().toLowerCase()) {
        loadCountryPool(expandedCountry);
      }
    } catch {
      toast.error('Failed to add first names');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAddLastNames = async () => {
    const names = parseNames(lastInput);
    if (!addCountry.trim()) { toast.error('Enter a country'); return; }
    if (names.length === 0) { toast.error('Enter at least one last name'); return; }
    setSubmitting(true);
    try {
      const res = await addLastNames(addCountry.trim(), names);
      toast.success(`${res.data.added} last name(s) added` + (res.data.skipped ? `, ${res.data.skipped} duplicate(s) skipped` : ''));
      setLastInput('');
      await loadStats();
      if (expandedCountry?.toLowerCase() === addCountry.trim().toLowerCase()) {
        loadCountryPool(expandedCountry);
      }
    } catch {
      toast.error('Failed to add last names');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteFirst = async (id) => {
    try {
      await deleteFirstName(id);
      toast.success('First name removed');
      await loadStats();
      if (expandedCountry) loadCountryPool(expandedCountry);
    } catch {
      toast.error('Failed to delete');
    }
  };

  const handleDeleteLast = async (id) => {
    try {
      await deleteLastName(id);
      toast.success('Last name removed');
      await loadStats();
      if (expandedCountry) loadCountryPool(expandedCountry);
    } catch {
      toast.error('Failed to delete');
    }
  };

  const loadTeams = useCallback(async () => {
    setTeamsLoading(true);
    try {
      const res = await getTeamList();
      setTeams(res.data.filter(t => !t.isBot)); // Only user teams
    } catch {
      toast.error('Failed to load teams');
    } finally {
      setTeamsLoading(false);
    }
  }, []);

  useEffect(() => { loadTeams(); }, [loadTeams]);

  const handleAssignSquad = async (teamId, teamName) => {
    if (!confirm(`Assign 15 players to ${teamName}? This will generate a new squad.`)) return;
    setAssigning(true);
    try {
      await assignSquadToTeam(teamId);
      toast.success(`Squad assigned to ${teamName} successfully!`);
      await loadTeams();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to assign squad');
    } finally {
      setAssigning(false);
    }
  };

  if (loading) {
    return (
      <div className="ap-page">
        <div className="ap-loading">Loading name pool data…</div>
      </div>
    );
  }

  return (
    <div className="ap-page">
      {/* ── Header ── */}
      <div className="ap-header">
        <div className="ap-header-left">
          <HiOutlineShieldCheck className="ap-header-icon" />
          <div>
            <h1>Admin — Name Pool</h1>
            <p>Add first names and last names per country. They combine to generate players for teams.</p>
          </div>
        </div>
      </div>

      {/* ── Stats ── */}
      <div className="ap-stats">
        <div className="ap-stat-card">
          <span className="ap-stat-label">First Names</span>
          <span className="ap-stat-value">{stats.totalFirstNames.toLocaleString()}</span>
        </div>
        <div className="ap-stat-card">
          <span className="ap-stat-label">Last Names</span>
          <span className="ap-stat-value">{stats.totalLastNames.toLocaleString()}</span>
        </div>
        <div className="ap-stat-card">
          <span className="ap-stat-label">Possible Combinations</span>
          <span className="ap-stat-value ap-stat-green">{stats.totalCombinations.toLocaleString()}</span>
        </div>
      </div>

      {/* ── Team Squad Assignment ── */}
      <div className="ap-card">
        <div className="ap-card-head">
          <h2><HiOutlineUserPlus /> Assign Squad to Teams</h2>
          <p>Use this to assign 15 players to teams that don't have any players yet.</p>
        </div>

        {teamsLoading ? (
          <div className="ap-loading">Loading teams…</div>
        ) : (
          <div className="ap-teams-list">
            {teams.map(team => (
              <div key={team.id} className="ap-team-row">
                <div className="ap-team-info">
                  <span className="ap-team-name">{team.teamName}</span>
                  <span className="ap-team-country">{team.country}</span>
                  <span className="ap-team-manager">{team.managerName}</span>
                </div>
                <button
                  className="ap-assign-btn"
                  onClick={() => handleAssignSquad(team.id, team.teamName)}
                  disabled={assigning}
                >
                  <HiOutlineUserPlus /> Assign Squad
                </button>
              </div>
            ))}
            {teams.length === 0 && <p className="ap-no-teams">No user teams found.</p>}
          </div>
        )}
      </div>

      {/* ── Add Names Form ── */}
      <div className="ap-card">
        <div className="ap-card-head">
          <h2>Add Names to Pool</h2>
        </div>

        <div className="ap-add-section">
          <div className="ap-country-input-row">
            <label className="ap-label">Country</label>
            <select
              className="ap-select"
              value={addCountry}
              onChange={(e) => setAddCountry(e.target.value)}
            >
              <option value="">Select a country</option>
              {COUNTRIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>

          <div className="ap-names-cols">
            <div className="ap-names-col">
              <label className="ap-label">First Names <span className="ap-hint">(one per line or comma separated)</span></label>
              <textarea
                className="ap-textarea"
                placeholder={"Gaurav, Saurav, Virat\nMushfiqur, Al Amin"}
                value={firstInput}
                onChange={(e) => setFirstInput(e.target.value)}
                rows={4}
              />
              <button
                className="ap-btn-primary"
                onClick={handleAddFirstNames}
                disabled={submitting}
              >
                <HiOutlinePlus /> Add First Names
              </button>
            </div>

            <div className="ap-names-col">
              <label className="ap-label">Last Names <span className="ap-hint">(one per line or comma separated)</span></label>
              <textarea
                className="ap-textarea"
                placeholder={"Sharma, Singh, Tyagi\nul Haq, Al Hasan"}
                value={lastInput}
                onChange={(e) => setLastInput(e.target.value)}
                rows={4}
              />
              <button
                className="ap-btn-primary"
                onClick={handleAddLastNames}
                disabled={submitting}
              >
                <HiOutlinePlus /> Add Last Names
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Country Pools ── */}
      <div className="ap-card">
        <div className="ap-card-head">
          <h2>Name Pools by Country</h2>
          <span className="ap-badge">{stats.countries.length} countr{stats.countries.length !== 1 ? 'ies' : 'y'}</span>
        </div>

        {stats.countries.length === 0 ? (
          <div className="ap-empty-msg">No names added yet. Use the form above to get started.</div>
        ) : (
          <div className="ap-country-list">
            {stats.countries.map((c) => (
              <div key={c.country} className="ap-country-item">
                <button className="ap-country-row" onClick={() => toggleCountry(c.country)}>
                  <span className="ap-country-chevron">
                    {expandedCountry === c.country ? <HiOutlineChevronDown /> : <HiOutlineChevronRight />}
                  </span>
                  <span className="ap-country-name">{c.country}</span>
                  <div className="ap-country-meta">
                    <span className="ap-tag">{c.firstNames} first</span>
                    <span className="ap-tag">{c.lastNames} last</span>
                    <span className="ap-tag ap-tag-combo">{c.combinations.toLocaleString()} combos</span>
                  </div>
                </button>

                {expandedCountry === c.country && (
                  <div className="ap-country-detail">
                    {poolLoading ? (
                      <div className="ap-pool-loading">Loading…</div>
                    ) : countryPool ? (
                      <div className="ap-pool-cols">
                        <div className="ap-pool-col">
                          <h4>First Names ({countryPool.firstNames.length})</h4>
                          <div className="ap-name-chips">
                            {countryPool.firstNames.map((fn) => (
                              <span key={fn.id} className="ap-chip">
                                {fn.name}
                                <button className="ap-chip-del" onClick={() => handleDeleteFirst(fn.id)} title="Remove">
                                  <HiOutlineTrash />
                                </button>
                              </span>
                            ))}
                            {countryPool.firstNames.length === 0 && <span className="ap-no-names">None yet</span>}
                          </div>
                        </div>
                        <div className="ap-pool-col">
                          <h4>Last Names ({countryPool.lastNames.length})</h4>
                          <div className="ap-name-chips">
                            {countryPool.lastNames.map((ln) => (
                              <span key={ln.id} className="ap-chip">
                                {ln.name}
                                <button className="ap-chip-del" onClick={() => handleDeleteLast(ln.id)} title="Remove">
                                  <HiOutlineTrash />
                                </button>
                              </span>
                            ))}
                            {countryPool.lastNames.length === 0 && <span className="ap-no-names">None yet</span>}
                          </div>
                        </div>
                      </div>
                    ) : null}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Team Assignment ── */}
      <div className="ap-card">
        <div className="ap-card-head">
          <h2>Assign Squads to Teams</h2>
        </div>

        {teamsLoading ? (
          <div className="ap-loading">Loading teams…</div>
        ) : (
          <div className="ap-teams-list">
            {teams.length === 0 ? (
              <div className="ap-no-teams">No teams found.</div>
            ) : (
              teams.map((team) => (
                <div key={team.id} className="ap-team-row">
                  <div className="ap-team-info">
                    <span className="ap-team-name">{team.teamName}</span>
                    <span className="ap-team-country">{team.country}</span>
                    <span className="ap-team-manager">{team.managerName}</span>
                  </div>
                  <button
                    className="ap-assign-btn"
                    onClick={() => handleAssignSquad(team.id, team.teamName)}
                    disabled={assigning}
                  >
                    <HiOutlineUserPlus />
                    {assigning ? 'Assigning...' : 'Assign Squad'}
                  </button>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
