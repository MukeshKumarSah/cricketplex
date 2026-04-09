import { useState, useEffect } from 'react';
import { useNavigate, useLocation, Routes, Route } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getCurrentSeason } from '../../api/auth';
import Sidebar from '../../components/Sidebar/Sidebar';
import Settings from '../Settings/Settings';
import Dashboard from '../Dashboard/Dashboard';
import GameManuals from '../GameManuals/GameManuals';
import Rules from '../Rules/Rules';
import Ground from '../Ground/Ground';
import TeamList from '../TeamList/TeamList';
import AdminPlayers from '../Admin/AdminPlayers';
import AdminLeagues from '../Admin/AdminLeagues';
import Squad from '../Squad/Squad';
import Player from '../Player/Player';
import Search from '../Search/Search';
import LeaguePage from '../League/LeaguePage';
import Matches from '../Matches/Matches';
import LineupSetup from '../Lineup/LineupSetup';
import FCStrategy from '../Lineup/FCStrategy';
import Challenges from '../Challenges/Challenges';
import MatchCenter from '../MatchCenter/MatchCenter';
import TeamProfile from '../TeamProfile/TeamProfile';
import FixturePreview from '../FixturePreview/FixturePreview';
import Stats from '../Stats/Stats';
import Academy from '../Academy/Academy';
import TransferMarket from '../TransferMarket/TransferMarket';
import Finances from '../Finances/Finances';
import GameFormulas from '../Admin/GameFormulas';
import AdminBots from '../Admin/AdminBots';
import ChatWidget from '../../components/ChatWidget/ChatWidget';
import TutorialTour from '../../components/TutorialTour/TutorialTour';
import './Home.css';
import {
  HiOutlineChevronDown,
  HiOutlineCog6Tooth,
  HiOutlineArrowRightOnRectangle,
  HiOutlineCalendarDays,
  HiOutlineClock,
  HiOutlineTrophy,
} from 'react-icons/hi2';

export default function Home() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [profileOpen, setProfileOpen] = useState(false);
  const [now, setNow] = useState(new Date());
  const [season, setSeason] = useState(null);

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    getCurrentSeason()
      .then((res) => setSeason(res.data.season))
      .catch(() => setSeason(1));
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)
    : '?';

  return (
    <div className="home-layout">
      <Sidebar isOpen={sidebarOpen} toggle={() => setSidebarOpen((p) => !p)} />

      <div className={`home-main ${sidebarOpen ? '' : 'sidebar-collapsed'}`}>
        {/* Top Bar */}
        <header className="top-bar">
          <div className="top-bar-left">
            <button
              className="menu-toggle"
              onClick={() => setSidebarOpen((p) => !p)}
              aria-label="Toggle sidebar"
            >
              ☰
            </button>
            <h2 className="page-title">Dashboard</h2>
          </div>

          <div className="top-bar-center">
            <div className="header-info-item">
              <HiOutlineCalendarDays className="header-info-icon" />
              <span className="header-info-text">
                {now.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' })}
              </span>
            </div>
            <div className="header-info-divider" />
            <div className="header-info-item">
              <HiOutlineClock className="header-info-icon" />
              <span className="header-info-text">
                {now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'UTC' })} UTC
              </span>
            </div>
            <div className="header-info-divider" />
            <div className="header-info-item">
              <HiOutlineTrophy className="header-info-icon" />
              <span className="header-info-text">Season {season ?? '...'}</span>
            </div>
          </div>

          <div className="top-bar-right">
            <div className="profile-wrapper">
              <button
                className="profile-btn"
                onClick={() => setProfileOpen((p) => !p)}
              >
                {user?.profilePicUrl ? (
                  <img
                    src={user.profilePicUrl}
                    alt="Profile"
                    className="profile-avatar"
                  />
                ) : (
                  <span className="profile-initials">{initials}</span>
                )}
                <HiOutlineChevronDown className="profile-chevron" />
              </button>

              {profileOpen && (
                <>
                  <div
                    className="profile-backdrop"
                    onClick={() => setProfileOpen(false)}
                  />
                  <div className="profile-dropdown">
                    <div className="profile-dropdown-header">
                      <span className="profile-dropdown-name">{user?.name}</span>
                      <span className="profile-dropdown-email">{user?.email}</span>
                    </div>
                    <div className="profile-dropdown-divider" />
                    <button
                      className="profile-dropdown-item"
                      onClick={() => {
                        setProfileOpen(false);
                        navigate('/settings');
                      }}
                    >
                      <HiOutlineCog6Tooth />
                      Settings
                    </button>
                    <button
                      className="profile-dropdown-item logout"
                      onClick={handleLogout}
                    >
                      <HiOutlineArrowRightOnRectangle />
                      Log Out
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        {/* Content Area */}
        <main className="home-content">
          <Routes>
            <Route
              path="settings"
              element={<Settings />}
            />
            <Route
              path="game-manuals"
              element={<GameManuals />}
            />
              <Route
                path="ground"
                element={<Ground />}
              />
            <Route
              path="rules"
              element={<Rules />}
            />
            <Route
              path="team-list"
              element={<TeamList />}
            />
            <Route
              path="team/:teamId"
              element={<TeamProfile />}
            />
            <Route
              path="squad"
              element={<Squad />}
            />
            <Route
              path="player/:id"
              element={<Player />}
            />
            <Route
              path="search"
              element={<Search />}
            />
            <Route
              path="stats"
              element={<Stats />}
            />
            <Route
              path="academy"
              element={<Academy />}
            />
            <Route
              path="transfer-market"
              element={<TransferMarket />}
            />
            <Route
              path="finances"
              element={<Finances />}
            />
            <Route
              path="league/:id"
              element={<LeaguePage />}
            />
            <Route
              path="matches"
              element={<Matches />}
            />
            <Route
              path="match/:fixtureId/lineup"
              element={<LineupSetup />}
            />
            <Route
              path="match/:fixtureId/fc-strategy"
              element={<FCStrategy />}
            />
            <Route
              path="match/:fixtureId/preview"
              element={<FixturePreview />}
            />
            <Route
              path="match/:fixtureId/scorecard"
              element={<MatchCenter />}
            />
            <Route
              path="match/:fixtureId/commentary"
              element={<MatchCenter />}
            />
            <Route
              path="match/:fixtureId/live"
              element={<MatchCenter />}
            />
            <Route
              path="challenges"
              element={<Challenges />}
            />
            {user?.role === 'ADMIN' && (
              <Route
                path="admin/players"
                element={<AdminPlayers />}
              />
            )}
            {user?.role === 'ADMIN' && (
              <Route
                path="admin/leagues"
                element={<AdminLeagues />}
              />
            )}
            {user?.role === 'ADMIN' && (
              <Route
                path="admin/formulas"
                element={<GameFormulas />}
              />
            )}
            {user?.role === 'ADMIN' && (
              <Route
                path="admin/bots"
                element={<AdminBots />}
              />
            )}
            <Route
              path="*"
              element={<Dashboard />}
            />
          </Routes>
        </main>
      </div>
      <ChatWidget />
      <TutorialTour sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />
    </div>
  );
}
