import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '../../context/NotificationContext';
import {
  HiOutlineBell,
  HiOutlineBolt,
  HiOutlineCheck,
  HiOutlineXMark,
  HiOutlineUserGroup,
  HiOutlineCheckCircle,
} from 'react-icons/hi2';
import './NotificationBell.css';

const TYPE_META = {
  CHALLENGE_RECEIVED: { icon: <HiOutlineBolt />,        color: '#f59e0b', label: 'Challenge' },
  CHALLENGE_ACCEPTED: { icon: <HiOutlineCheck />,       color: '#10b981', label: 'Accepted' },
  CHALLENGE_DECLINED: { icon: <HiOutlineXMark />,       color: '#ef4444', label: 'Declined' },
  ADDED_TO_GROUP:     { icon: <HiOutlineUserGroup />,   color: '#22d3ee', label: 'Group' },
};

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

export default function NotificationBell() {
  const navigate = useNavigate();
  const { notifications, unreadCount, fetchNotifications, markRead, markAllRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const panelRef = useRef(null);

  // Load full list when panel opens
  useEffect(() => {
    if (open) fetchNotifications();
  }, [open, fetchNotifications]);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const handleClick = (n) => {
    if (!n.read) markRead(n.id);
    if (n.link) {
      navigate(n.link);
      setOpen(false);
    }
  };

  const handleMarkAllRead = (e) => {
    e.stopPropagation();
    markAllRead();
  };

  return (
    <div className="nb-wrapper" ref={panelRef}>
      <button
        className={`nb-btn ${open ? 'active' : ''}`}
        onClick={() => setOpen((p) => !p)}
        aria-label="Notifications"
      >
        <HiOutlineBell className="nb-icon" />
        {unreadCount > 0 && (
          <span className="nb-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      {open && (
        <div className="nb-panel">
          <div className="nb-header">
            <span className="nb-title">Notifications</span>
            {unreadCount > 0 && (
              <button className="nb-mark-all" onClick={handleMarkAllRead}>
                <HiOutlineCheckCircle /> Mark all read
              </button>
            )}
          </div>

          <div className="nb-list">
            {notifications.length === 0 ? (
              <div className="nb-empty">No notifications yet</div>
            ) : (
              notifications.map((n) => {
                const meta = TYPE_META[n.type] || { icon: <HiOutlineBell />, color: '#94a3b8', label: n.type };
                return (
                  <div
                    key={n.id}
                    className={`nb-item ${!n.read ? 'unread' : ''} ${n.link ? 'clickable' : ''}`}
                    onClick={() => handleClick(n)}
                  >
                    <div className="nb-item-icon" style={{ color: meta.color, background: meta.color + '18' }}>
                      {meta.icon}
                    </div>
                    <div className="nb-item-body">
                      <span className="nb-item-title">{n.title}</span>
                      <span className="nb-item-text">{n.body}</span>
                      <span className="nb-item-time">{timeAgo(n.createdAt)}</span>
                    </div>
                    {!n.read && <div className="nb-item-dot" />}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
