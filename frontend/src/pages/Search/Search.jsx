import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { searchManagers, searchPlayers, searchTeams, searchLeagues } from '../../api/auth';
import toast from 'react-hot-toast';
import {
  HiOutlineMagnifyingGlass,
  HiOutlineUser,
  HiOutlineUserGroup,
  HiOutlineTrophy,
  HiOutlineGlobeAlt,
} from 'react-icons/hi2';
import './Search.css';

const TABS = [
  { key: 'managers', label: 'Managers', icon: HiOutlineUser },
  { key: 'players', label: 'Players', icon: HiOutlineUserGroup },
  { key: 'teams', label: 'Teams', icon: HiOutlineTrophy },
  { key: 'leagues', label: 'Leagues', icon: HiOutlineGlobeAlt },
];

export default function Search() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [activeTab, setActiveTab] = useState('managers');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) {
      toast.error('Enter a search term');
      return;
    }
    setLoading(true);
    setSearched(true);
    try {
      let res;
      if (activeTab === 'managers') {
        res = await searchManagers(query.trim());
      } else if (activeTab === 'players') {
        res = await searchPlayers(query.trim());
      } else if (activeTab === 'teams') {
        res = await searchTeams(query.trim());
      } else if (activeTab === 'leagues') {
        res = await searchLeagues(query.trim());
      } else {
        setResults([]);
        setLoading(false);
        return;
      }
      setResults(res.data);
    } catch {
      toast.error('Search failed');
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch();
  };

  const switchTab = (key) => {
    setActiveTab(key);
    setResults([]);
    setSearched(false);
    setQuery('');
  };

  const getPlaceholder = () => {
    switch (activeTab) {
      case 'managers': return 'Search by manager name...';
      case 'players': return 'Search by player first or last name...';
      case 'teams': return 'Search by team name...';
      case 'leagues': return 'Search by country name (e.g. India, Australia)...';
      default: return 'Search...';
    }
  };

  return (
    <div className="search-page">
      <h1 className="search-title">Search</h1>

      {/* Tabs */}
      <div className="search-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`search-tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => switchTab(tab.key)}
          >
            <tab.icon className="search-tab-icon" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Search Bar */}
      <div className="search-bar">
        <HiOutlineMagnifyingGlass className="search-bar-icon" />
        <input
          type="text"
          className="search-input"
          placeholder={getPlaceholder()}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button
          className="search-btn"
          onClick={handleSearch}
          disabled={loading}
        >
          {loading ? 'Searching...' : 'Search'}
        </button>
      </div>

      {/* Results */}
      <div className="search-results">
        {!searched && !loading && (
          <div className="search-empty">
            <HiOutlineMagnifyingGlass className="search-empty-icon" />
            <p>Enter a search term and hit Search or press Enter.</p>
          </div>
        )}

        {searched && !loading && results.length === 0 && (
          <div className="search-empty">
            <p>No results found for "{query}"</p>
          </div>
        )}

        {/* Manager Results */}
        {activeTab === 'managers' && results.length > 0 && (
          <div className="search-grid">
            {results.map((m) => (
              <div
                key={m.id}
                className={`search-card ${m.teamId ? 'search-card-clickable' : ''}`}
                onClick={() => {
                  if (!m.teamId) return;
                  navigate(m.teamId === user?.teamId ? '/' : `/team/${m.teamId}`);
                }}
              >
                <div className="search-card-avatar">
                  {m.profilePicUrl ? (
                    <img src={`/api/files/${m.profilePicUrl}`} alt={m.name} />
                  ) : (
                    <span className="search-card-initials">
                      {m.name?.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)}
                    </span>
                  )}
                </div>
                <div className="search-card-info">
                  <span className="search-card-name">{m.name}</span>
                  <span className="search-card-meta">@{m.username}</span>
                  {isAdmin && (
                    <span className={`search-card-badge ${m.teamSetupDone ? 'badge-green' : 'badge-yellow'}`}>
                      {m.teamSetupDone ? 'Has Team' : 'No Team'}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Player Results */}
        {activeTab === 'players' && results.length > 0 && (
          <div className="search-grid">
            {results.map((p) => (
              <div
                key={p.id}
                className="search-card search-card-clickable"
                onClick={() => navigate(`/player/${p.id}`)}
              >
                <div className="search-card-role-badge" data-role={p.role}>
                  {p.role === 'ALL_ROUNDER' ? 'AR' : p.role?.slice(0, 3)}
                </div>
                <div className="search-card-info">
                  <span className="search-card-name">{p.firstName} {p.lastName}</span>
                  <span className="search-card-meta">
                    {p.nationality} · Age {p.age}yr {p.ageDays ?? 0}d · ⭐ {p.rating}
                  </span>
                  <span className="search-card-meta">
                    Team: {p.teamName}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Team Results */}
        {activeTab === 'teams' && results.length > 0 && (
          <div className="search-grid">
            {results.map((t) => (
              <div
                key={t.id}
                className="search-card search-card-clickable"
                onClick={() => navigate(t.id === user?.teamId ? '/' : `/team/${t.id}`)}
              >
                <div className="search-card-avatar">
                  {t.teamProfilePicUrl ? (
                    <img src={`/api/files/${t.teamProfilePicUrl}`} alt={t.teamName} />
                  ) : (
                    <span className="search-card-initials">
                      {t.teamName?.slice(0, 2).toUpperCase()}
                    </span>
                  )}
                </div>
                <div className="search-card-info">
                  <span className="search-card-name">{t.teamName}</span>
                  <span className="search-card-meta">{t.country} · Manager: {t.managerName}</span>
                  <div className="search-card-ratings">
                    <span>ODI: {t.odiRating}</span>
                    <span>T20: {t.t20Rating}</span>
                    <span>FC: {t.fcRating}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* League Results */}
        {activeTab === 'leagues' && results.length > 0 && (
          <div className="search-grid">
            {results.map((l) => (
              <div
                key={l.id}
                className="search-card search-card-clickable"
                onClick={() => navigate(`/league/${l.id}`)}
              >
                <div className="search-card-role-badge" data-role="LEAGUE">
                  {l.leagueId}
                </div>
                <div className="search-card-info">
                  <span className="search-card-name">{l.country} {l.format} {l.leagueId}</span>
                  <span className="search-card-meta">
                    {l.format} · Division {l.division} · League {l.leagueNumber} · Season {l.season}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
