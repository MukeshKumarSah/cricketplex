import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { HiOutlineCpuChip, HiChevronUp, HiChevronDown, HiChevronUpDown } from 'react-icons/hi2';
import { getTeamList } from '../../api/auth';
import '../TeamList/TeamList.css';

const ratingColor = (r) => {
  if (r >= 1400) return '#22d3ee';
  if (r >= 1200) return '#a3e635';
  if (r >= 1000) return '#fbbf24';
  if (r >= 800)  return '#f97316';
  return '#ef4444';
};

export default function AdminBots() {
  const navigate = useNavigate();
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
        setTeams(res.data.filter((t) => t.isBot));
      } catch {
        toast.error('Failed to load bot teams');
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
        t.teamName.toLowerCase().includes(q) ||
        t.country.toLowerCase().includes(q)
    );
    list.sort((a, b) => {
      let aVal, bVal;
      switch (sortKey) {
        case 'teamName':   aVal = a.teamName.toLowerCase(); bVal = b.teamName.toLowerCase(); break;
        case 'odiRating':  aVal = a.odiRating; bVal = b.odiRating; break;
        case 't20Rating':  aVal = a.t20Rating; bVal = b.t20Rating; break;
        case 'fcRating':   aVal = a.fcRating; bVal = b.fcRating; break;
        case 'country':    aVal = a.country.toLowerCase(); bVal = b.country.toLowerCase(); break;
        default:           aVal = a.teamName.toLowerCase(); bVal = b.teamName.toLowerCase();
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
        <div className="tl-loading">Loading bot teams…</div>
      </div>
    );
  }

  return (
    <div className="tl-page">
      <div className="tl-header">
        <div className="tl-header-left">
          <HiOutlineCpuChip className="tl-header-icon" />
          <div>
            <h1>Bot Teams</h1>
            <p>{teams.length} bot teams in the game</p>
          </div>
        </div>
        <div className="tl-header-right">
          <input
            className="tl-search"
            type="text"
            placeholder="Search bot teams, nations…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="tl-card">
        <div className="tl-table-wrap">
          <table className="tl-table">
            <thead>
              <tr>
                {[
                  { key: 'teamName', label: 'Team', cls: 'tl-th-team' },
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
                  <td colSpan={5} className="tl-empty">
                    No bot teams found.
                  </td>
                </tr>
              ) : (
                filtered.map((team) => (
                  <tr
                    key={team.id}
                    className="tl-row tl-row-clickable"
                    onClick={() => navigate(`/team/${team.id}`)}
                  >
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
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className="tl-footer">
          <span>{filtered.length} bot team{filtered.length !== 1 ? 's' : ''}</span>
        </div>
      </div>
    </div>
  );
}
