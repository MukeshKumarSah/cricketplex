import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { HiOutlineQueueList, HiChevronUp, HiChevronDown, HiChevronUpDown } from 'react-icons/hi2';
import { getTeamList } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import './TeamList.css';

const STATUS_META = {
  GREEN:  { color: '#22c55e', label: 'Online' },
  YELLOW: { color: '#eab308', label: 'Active today' },
  GREY:   { color: '#64748b', label: 'This week' },
  ORANGE: { color: '#f97316', label: 'This month' },
  RED:    { color: '#ef4444', label: 'Inactive' },
};

const ratingColor = (r) => {
  if (r >= 1400) return '#22d3ee';
  if (r >= 1200) return '#a3e635';
  if (r >= 1000) return '#fbbf24';
  if (r >= 800)  return '#f97316';
  return '#ef4444';
};

const STATUS_ORDER = { GREEN: 0, YELLOW: 1, GREY: 2, ORANGE: 3, RED: 4 };

export default function TeamList() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState('teamName');
  const [sortDir, setSortDir] = useState('asc');

  const toggleSort = (key) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  useEffect(() => {
    const load = async () => {
      try {
        const res = await getTeamList();
        setTeams(res.data);
      } catch {
        toast.error('Failed to load team list');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    const list = teams.filter(
      (t) =>
        !t.isBot &&
        (t.teamName.toLowerCase().includes(q) ||
        t.managerName.toLowerCase().includes(q) ||
        t.country.toLowerCase().includes(q))
    );
    list.sort((a, b) => {
      let aVal, bVal;
      switch (sortKey) {
        case 'teamName':     aVal = a.teamName.toLowerCase(); bVal = b.teamName.toLowerCase(); break;
        case 'managerName':  aVal = a.managerName.toLowerCase(); bVal = b.managerName.toLowerCase(); break;
        case 'odiRating':    aVal = a.odiRating; bVal = b.odiRating; break;
        case 't20Rating':    aVal = a.t20Rating; bVal = b.t20Rating; break;
        case 'fcRating':     aVal = a.fcRating; bVal = b.fcRating; break;
        case 'country':      aVal = a.country.toLowerCase(); bVal = b.country.toLowerCase(); break;
        case 'activity':     aVal = STATUS_ORDER[a.activityStatus] ?? 4; bVal = STATUS_ORDER[b.activityStatus] ?? 4; break;
        default:             aVal = a.teamName.toLowerCase(); bVal = b.teamName.toLowerCase();
      }
      if (aVal < bVal) return sortDir === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
    return list;
  }, [teams, search, sortKey, sortDir]);

  if (loading) {
    return (
      <div className="tl-page">
        <div className="tl-loading">Loading teams…</div>
      </div>
    );
  }

  return (
    <div className="tl-page">
      {/* ── Header ── */}
      <div className="tl-header">
        <div className="tl-header-left">
          <HiOutlineQueueList className="tl-header-icon" />
          <div>
            <h1>Team List</h1>
            <p>All registered teams and their managers</p>
          </div>
        </div>
        <div className="tl-header-right">
          <input
            className="tl-search"
            type="text"
            placeholder="Search teams, managers, nations…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* ── Table ── */}
      <div className="tl-card">
        <div className="tl-table-wrap">
          <table className="tl-table">
            <thead>
              <tr>
                {[
                  { key: 'teamName', label: 'Team', cls: 'tl-th-team' },
                  { key: 'managerName', label: 'Manager', cls: '' },
                  { key: 'odiRating', label: 'ODI', cls: 'tl-th-center' },
                  { key: 't20Rating', label: 'T20', cls: 'tl-th-center' },
                  { key: 'fcRating', label: 'FC', cls: 'tl-th-center' },
                  { key: 'country', label: 'Nation', cls: '' },
                ].map((col) => (
                  <th
                    key={col.key}
                    className={`tl-th-sortable ${col.cls}`}
                    onClick={() => toggleSort(col.key)}
                  >
                    <span className="tl-th-inner">
                      {col.label}
                      {sortKey === col.key ? (
                        sortDir === 'asc' ? <HiChevronUp className="tl-sort-icon active" /> : <HiChevronDown className="tl-sort-icon active" />
                      ) : (
                        <HiChevronUpDown className="tl-sort-icon" />
                      )}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="tl-empty">
                    No teams found.
                  </td>
                </tr>
              ) : (
                filtered.map((team) => {
                  const status = STATUS_META[team.activityStatus] || STATUS_META.RED;
                  return (
                    <tr key={team.id} className="tl-row tl-row-clickable" onClick={() => navigate(team.id === user?.teamId ? '/' : `/team/${team.id}`)}>
                      <td className="tl-cell-team">
                        <div className="tl-team-info">
                          {team.teamProfilePicUrl ? (
                            <img
                              src={`/api/files/${team.teamProfilePicUrl}`}
                              alt=""
                              className="tl-team-avatar"
                            />
                          ) : (
                            <span className="tl-team-avatar-placeholder">
                              {team.teamName.charAt(0)}
                            </span>
                          )}
                          <span className="tl-team-name">{team.teamName}</span>
                        </div>
                      </td>
                      <td className="tl-cell-manager">
                        <div className="tl-manager-wrap">
                          <div className="tl-manager-dot-wrap">
                            {team.managerProfilePicUrl ? (
                              <img
                                src={`/api/files/${team.managerProfilePicUrl}`}
                                alt=""
                                className="tl-manager-avatar"
                              />
                            ) : (
                              <span className="tl-manager-avatar-placeholder">
                                {team.managerName.charAt(0)}
                              </span>
                            )}
                            <span
                              className="tl-status-dot"
                              style={{ background: status.color }}
                              title={status.label}
                            />
                          </div>
                          <div className="tl-manager-info">
                            <span className="tl-manager-name">{team.managerName}</span>
                            <span className="tl-manager-status" style={{ color: status.color }}>
                              {status.label}
                            </span>
                          </div>
                        </div>
                      </td>
                      <td className="tl-cell-rating">
                        <span className="tl-rating" style={{ color: ratingColor(team.odiRating) }}>
                          {team.odiRating}
                        </span>
                      </td>
                      <td className="tl-cell-rating">
                        <span className="tl-rating" style={{ color: ratingColor(team.t20Rating) }}>
                          {team.t20Rating}
                        </span>
                      </td>
                      <td className="tl-cell-rating">
                        <span className="tl-rating" style={{ color: ratingColor(team.fcRating) }}>
                          {team.fcRating}
                        </span>
                      </td>
                      <td className="tl-cell-nation">
                        <span className="tl-nation">{team.country}</span>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        <div className="tl-footer">
          <span>{filtered.length} team{filtered.length !== 1 ? 's' : ''}</span>
        </div>
      </div>
    </div>
  );
}
