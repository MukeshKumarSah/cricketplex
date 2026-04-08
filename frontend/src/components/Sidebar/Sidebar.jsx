import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  HiOutlineHome,
  HiOutlineUserGroup,
  HiOutlineTrophy,
  HiOutlineAcademicCap,
  HiOutlineBanknotes,
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
} from 'react-icons/hi2';
import './Sidebar.css';

const menuItems = [
  { label: 'Dashboard', icon: HiOutlineHome, path: '/' },
  { label: 'Squad', icon: HiOutlineUserGroup, path: '/squad' },
  { label: 'Matches', icon: HiOutlineTrophy, path: '/matches' },
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
  { label: 'ChangeLogs', icon: HiOutlineClipboardDocumentList, path: '/changelogs' },
  { label: 'Support the Game', icon: HiOutlineHeart, path: '/support' },
  { label: 'Search', icon: HiOutlineMagnifyingGlass, path: '/search' },
];

const adminItems = [
  { label: 'Player Pool', icon: HiOutlineShieldCheck, path: '/admin/players' },
  { label: 'Leagues', icon: HiOutlineGlobeAlt, path: '/admin/leagues' },
  { label: 'Bot Teams', icon: HiOutlineCpuChip, path: '/admin/bots' },
  { label: 'Game Formulas', icon: HiOutlineCalculator, path: '/admin/formulas' },
];

export default function Sidebar({ isOpen, toggle }) {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  return (
    <aside className={`sidebar ${isOpen ? 'open' : 'collapsed'}`}>
      <div className="sidebar-brand">
        <span className="sidebar-logo">🏏</span>
        {isOpen && <span className="sidebar-title">CricketPlex</span>}
      </div>

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
