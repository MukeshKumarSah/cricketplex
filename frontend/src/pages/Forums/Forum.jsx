import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getForumCategories, getForumPanel, createForumCategory } from '../../api/auth';
import { HiOutlineChatBubbleLeftRight, HiOutlinePlus, HiOutlineChevronRight, HiOutlineChatBubbleOvalLeft } from 'react-icons/hi2';
import toast from 'react-hot-toast';
import './Forum.css';

function timeAgo(dateStr) {
  if (!dateStr) return '';
  // Treat as UTC if no timezone info is present
  const utc = (dateStr.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(dateStr)) ? dateStr : dateStr + 'Z';
  const diff = Date.now() - new Date(utc).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1)  return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs  < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(utc).toLocaleDateString();
}

export default function Forum() {
  const { user } = useAuth();
  const navigate  = useNavigate();
  const isAdmin   = user?.role === 'ADMIN';

  const [categories, setCategories] = useState([]);
  const [panel,      setPanel]      = useState(null);
  const [loading,    setLoading]    = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newCat,     setNewCat]     = useState({ name: '', description: '' });
  const [saving,     setSaving]     = useState(false);

  useEffect(() => {
    Promise.all([getForumCategories(), getForumPanel()])
      .then(([catRes, panelRes]) => {
        setCategories(catRes.data);
        setPanel(panelRes.data);
      })
      .catch(() => toast.error('Failed to load forums'))
      .finally(() => setLoading(false));
  }, []);

  const handleCreateCategory = async (e) => {
    e.preventDefault();
    if (!newCat.name.trim()) return;
    setSaving(true);
    try {
      const res = await createForumCategory(newCat);
      toast.success('Category created');
      setCategories((prev) => [...prev, { ...res.data, threadCount: 0, latestThread: null }]);
      setShowCreate(false);
      setNewCat({ name: '', description: '' });
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to create category');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="forum-loading">Loading forums…</div>;

  return (
    <div className="forum-page">
      {/* ── Header ── */}
      <div className="forum-header">
        <div className="forum-header-left">
          <HiOutlineChatBubbleLeftRight className="forum-header-icon" />
          <div>
            <h1 className="forum-title">Forums</h1>
            <p className="forum-subtitle">Discuss tactics, share ideas, connect with managers</p>
          </div>
        </div>
        {isAdmin && (
          <button className="btn-primary" onClick={() => setShowCreate(true)}>
            <HiOutlinePlus /> New Category
          </button>
        )}
      </div>

      <div className="forum-layout">
        {/* ── Main: Category list ── */}
        <div className="forum-main">
          {categories.map((cat) => (
            <div key={cat.id} className="forum-category-card">
              {/* ── Category header ── */}
              <div
                className="fcc-header"
                onClick={() => navigate(`category/${cat.id}`)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === 'Enter' && navigate(`category/${cat.id}`)}
              >
                <div className="fcc-icon">
                  <HiOutlineChatBubbleOvalLeft />
                </div>
                <div className="fcc-info">
                  <h2 className="fcc-name">{cat.name}</h2>
                  {cat.description && <p className="fcc-desc">{cat.description}</p>}
                </div>
                <div className="fcc-meta">
                  <span className="fcc-count">{cat.threadCount ?? 0} threads</span>
                </div>
                <HiOutlineChevronRight className="fcc-arrow" />
              </div>

              {/* ── Recent threads ── */}
              {cat.recentThreads && cat.recentThreads.length > 0 && (
                <div className="fcc-thread-list">
                  {cat.recentThreads.map((t) => (
                    <div
                      key={t.id}
                      className="fcc-thread-row"
                      onClick={(e) => { e.stopPropagation(); navigate(`thread/${t.id}`); }}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === 'Enter' && navigate(`thread/${t.id}`)}
                    >
                      <span className="fcc-tr-avatar">{t.authorName?.charAt(0) ?? '?'}</span>
                      <span className="fcc-tr-title">{t.title}</span>
                      <span className="fcc-tr-meta">
                        <HiOutlineChatBubbleLeftRight className="fcc-tr-icon" />
                        {t.commentCount}
                        <span className="fcc-tr-dot">·</span>
                        {timeAgo(t.lastActivityAt)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}

          {categories.length === 0 && (
            <div className="forum-empty">No categories yet.</div>
          )}
        </div>

        {/* ── Sidebar: Panel ── */}
        <aside className="forum-sidebar">
          {panel && (
            <>
              <PanelCard
                title="Most Active"
                threads={panel.mostActiveThreads}
                badge={(t) => `${t.commentCount} replies`}
                navigate={navigate}
              />
              <PanelCard
                title="Recently Created"
                threads={panel.recentlyCreatedThreads}
                badge={(t) => timeAgo(t.createdAt)}
                navigate={navigate}
              />
              <PanelCard
                title="Recent Activity"
                threads={panel.recentActivityThreads}
                badge={(t) => timeAgo(t.lastActivityAt)}
                navigate={navigate}
              />
            </>
          )}
        </aside>
      </div>

      {/* ── Admin: Create Category Modal ── */}
      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-box" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">New Category</h2>
            <form onSubmit={handleCreateCategory}>
              <div className="form-group">
                <label>Name</label>
                <input
                  value={newCat.name}
                  onChange={(e) => setNewCat((p) => ({ ...p, name: e.target.value }))}
                  placeholder="Category name"
                  required
                />
              </div>
              <div className="form-group">
                <label>Description (optional)</label>
                <input
                  value={newCat.description}
                  onChange={(e) => setNewCat((p) => ({ ...p, description: e.target.value }))}
                  placeholder="Short description"
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Creating…' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function PanelCard({ title, threads, badge, navigate }) {
  if (!threads || threads.length === 0) return (
    <div className="panel-card">
      <h3 className="panel-card-title">{title}</h3>
      <p className="panel-empty">No threads yet</p>
    </div>
  );
  return (
    <div className="panel-card">
      <h3 className="panel-card-title">{title}</h3>
      <div className="panel-thread-list">
        {threads.map((thread) => (
          <div
            key={thread.id}
            className="panel-thread"
            onClick={() => navigate(`thread/${thread.id}`)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && navigate(`thread/${thread.id}`)}
          >
            <p className="panel-thread-title">{thread.title}</p>
            <div className="panel-thread-meta">
              <span className="panel-cat-name">{thread.categoryName}</span>
              <span className="panel-badge">{badge(thread)}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
