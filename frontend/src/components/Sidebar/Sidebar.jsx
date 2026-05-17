import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useEffect, useState } from 'react';
import { getPendingChallengeCount } from '../../api/auth';
import TeamSwitcher from '../TeamSwitcher/TeamSwitcher';
import {
  HiOutlineHome,
  HiOutlineUserGroup,
  HiOutlineTrophy,
  HiOutlineAcademicCap,  HiOutlineBanknotes,
  HiOutlineArrowsRightLeft,
  HiOutlineBuildingOffice2,
  HiOutlineChartBar,
  HiOutlineQueueList,
  HiOutlineChatBubbleLeftRight,
  HiOutlineBookOpen,
  HiOutlineDocumentText,
  HiOutlineClipboardDocumentList,
  HiOutlinePencilSquare,
  HiOutlineHeart,
  HiOutlineShieldCheck,
  HiOutlineMagnifyingGlass,
  HiOutlineGlobeAlt,
  HiOutlineBolt,
  HiOutlineCalculator,
  HiOutlineCpuChip,
  HiOutlineBeaker,
  HiOutlineCalendarDays,
  HiOutlineStar,
  HiOutlineFlag,
} from 'react-icons/hi2';
import './Sidebar.css';

const menuItems = [
  { label: 'Dashboard', icon: HiOutlineHome, path: '/' },
  { label: 'Squad', icon: HiOutlineUserGroup, path: '/squad' },
  { label: 'Matches', icon: HiOutlineTrophy, path: '/matches' },
  { label: 'Cup', icon: HiOutlineStar, path: '/cup' },
  { label: 'Tournaments', icon: HiOutlineFlag, path: '/tournaments' },
  { label: 'Challenges', icon: HiOutlineBolt, path: '/challenges' },
  { label: 'Academy', icon: HiOutlineAcademicCap, path: '/academy' },
  { label: 'Finances', icon: HiOutlineBanknotes, path: '/finances' },
  { label: 'Transfer Market', icon: HiOutlineArrowsRightLeft, path: '/transfer-market' },
  { label: 'Ground', icon: HiOutlineBuildingOffice2, path: '/ground' },
  { label: 'Stats', icon: HiOutlineChartBar, path: '/stats' },
  { label: 'Team List', icon: HiOutlineQueueList, path: '/team-list' },
  { label: 'Forums', icon: HiOutlineChatBubbleLeftRight, path: '/forums' },
  { label: 'Blogs', icon: HiOutlinePencilSquare, path: '/blogs' },
  { label: 'Game Manuals', icon: HiOutlineBookOpen, path: '/game-manuals' },
  { label: 'Rules & Regulations', icon: HiOutlineDocumentText, path: '/rules' },
  { label: 'Season Calendar', icon: HiOutlineCalendarDays, path: '/calendar' },
  { label: 'ChangeLogs', icon: HiOutlineClipboardDocumentList, path: '/changelogs' },
  { label: 'Support the Game', icon: HiOutlineHeart, path: '/support' },
  { label: 'Search', icon: HiOutlineMagnifyingGlass, path: '/search' },
];

const adminItems = [
  { label: 'Player Pool', icon: HiOutlineShieldCheck, path: '/admin/players' },
  { label: 'Leagues', icon: HiOutlineGlobeAlt, path: '/admin/leagues' },
  { label: 'Bot Teams', icon: HiOutlineCpuChip, path: '/admin/bots' },
  { label: 'Game Formulas', icon: HiOutlineCalculator, path: '/admin/formulas' },
  { label: 'Sim Lab', icon: HiOutlineBeaker, path: '/admin/sim' },
];

export default function Sidebar({ isOpen, toggle }) {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const location = useLocation();
  const [pendingChallenges, setPendingChallenges] = useState(0);

  useEffect(() => {
    if (!user) return;
    const fetch = () =>
      getPendingChallengeCount()
        .then((res) => setPendingChallenges(res.data.count || 0))
        .catch(() => {});
    fetch();
    const interval = setInterval(fetch, 60_000); // refresh every 60s
    return () => clearInterval(interval);
  }, [user, location.pathname]); // also refresh when navigating

  return (
    <aside className={`sidebar ${isOpen ? 'open' : 'collapsed'}`}>
      <div className="sidebar-brand">
        <span className="sidebar-logo">🏏</span>
        {isOpen && <span className="sidebar-title">CricketPlex</span>}
      </div>

      {/* Team Switcher - only show when sidebar is open */}
      {isOpen && (
        <div className="sidebar-team-switcher">
          <TeamSwitcher />
        </div>
      )}

      <nav className="sidebar-nav">
        {menuItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `sidebar-link ${isActive ? 'active' : ''}`
            }
            title={item.label}
            data-tour={item.path === '/' ? 'dashboard' : item.path.replace('/', '')}
          >
            <item.icon className="sidebar-icon" />
            {isOpen && <span className="sidebar-label">{item.label}</span>}
            {item.path === '/challenges' && pendingChallenges > 0 && (
              <span className="sidebar-badge">{pendingChallenges > 99 ? '99+' : pendingChallenges}</span>
            )}
          </NavLink>
        ))}

        {isAdmin && (
          <>
            {isOpen && <div className="sidebar-divider"><span>Admin</span></div>}
            {!isOpen && <div className="sidebar-divider-collapsed" />}
            {adminItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `sidebar-link sidebar-link-admin ${isActive ? 'active' : ''}`
                }
                title={item.label}
              >
                <item.icon className="sidebar-icon" />
                {isOpen && <span className="sidebar-label">{item.label}</span>}
              </NavLink>
            ))}
          </>
        )}
      </nav>
    </aside>
  );
}
