import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  getForumThreads,
  createForumThread,
  pinForumThread,
  lockForumThread,
  deleteForumThread,
} from '../../api/auth';
import {
  HiOutlineArrowLeft,
  HiOutlinePlus,
  HiOutlineStar,
  HiStar,
  HiOutlineChatBubbleOvalLeft,
  HiOutlineLockClosed,
  HiOutlineChevronLeft,
  HiOutlineChevronRight,
} from 'react-icons/hi2';
import toast from 'react-hot-toast';
import RichTextEditor from '../../components/RichTextEditor/RichTextEditor';
import './ForumCategory.css';

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1)  return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs  < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

export default function ForumCategory() {
  const { categoryId } = useParams();
  const navigate       = useNavigate();
  const { user: authUser } = useAuth();
  const isAdmin = authUser?.role === 'ADMIN';

  const [data,       setData]       = useState(null);
  const [loading,    setLoading]    = useState(true);
  const [page,       setPage]       = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [form,       setForm]       = useState({ title: '', body: '' });
  const [saving,     setSaving]     = useState(false);

  const load = useCallback((p = 0) => {
    setLoading(true);
    getForumThreads(categoryId, p)
      .then((r) => { setData(r.data); setPage(p); })
      .catch(() => toast.error('Failed to load threads'))
      .finally(() => setLoading(false));
  }, [categoryId]);

  useEffect(() => { load(0); }, [load]);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!form.title.trim() || !form.body.trim()) return;
    setSaving(true);
    try {
      await createForumThread(categoryId, form);
      toast.success('Thread created!');
      setShowCreate(false);
      setForm({ title: '', body: '' });
      load(0);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to create thread');
    } finally {
      setSaving(false);
    }
  };

  const handlePin = async (thread) => {
    try {
      await pinForumThread(thread.id, !thread.isPinned);
      toast.success(thread.isPinned ? 'Unpinned' : 'Pinned');
      load(page);
    } catch {
      toast.error('Failed');
    }
  };

  const handleLock = async (thread) => {
    try {
      await lockForumThread(thread.id, !thread.isLocked);
      toast.success(thread.isLocked ? 'Unlocked' : 'Locked');
      load(page);
    } catch {
      toast.error('Failed');
    }
  };

  const handleDelete = async (thread) => {
    if (!window.confirm(`Delete thread "${thread.title}"?`)) return;
    try {
      await deleteForumThread(thread.id);
      toast.success('Thread deleted');
      load(page);
    } catch {
      toast.error('Failed to delete');
    }
  };

  if (loading && !data) return <div className="fc-loading">Loading…</div>;

  const category   = data?.category;
  const threads    = data?.threads ?? [];
  const totalPages = data?.totalPages ?? 1;

  return (
    <div className="fc-page">
      {/* ── Back + header ── */}
      <div className="fc-header">
        <button className="btn-ghost fc-back" onClick={() => navigate('/forums')}>
          <HiOutlineArrowLeft /> Forums
        </button>
        <div className="fc-header-info">
          <h1 className="fc-title">{category?.name}</h1>
          {category?.description && <p className="fc-subtitle">{category.description}</p>}
        </div>
        <button className="btn-primary" onClick={() => setShowCreate(true)}>
          <HiOutlinePlus /> New Thread
        </button>
      </div>

      {/* ── Thread list ── */}
      <div className="fc-thread-list">
        {loading && <div className="fc-loading">Loading…</div>}

        {!loading && threads.length === 0 && (
          <div className="fc-empty">No threads yet. Be the first to start a discussion!</div>
        )}

        {threads.map((t) => (
          <div key={t.id} className="fc-thread-row">
            {/* Star */}
            <div className="fc-star" title={t.hasPosted ? 'You have posted in this thread' : "You haven't posted here"}>
              {t.hasPosted
                ? <HiStar className="star-red" />
                : <HiOutlineStar className="star-yellow" />}
            </div>

            {/* Thread info */}
            <div
              className="fc-thread-info"
              onClick={() => navigate(`/forums/thread/${t.id}`)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && navigate(`/forums/thread/${t.id}`)}
            >
              <div className="fc-thread-title-row">
                {t.isPinned && <span className="badge-pin">Pinned</span>}
                {t.isLocked && <HiOutlineLockClosed className="lock-icon" title="Locked" />}
                <span className="fc-thread-title">{t.title}</span>
              </div>
              <div className="fc-thread-meta">
                <span>by <strong>{t.authorName}</strong></span>
                <span>·</span>
                <span>{timeAgo(t.lastActivityAt)}</span>
              </div>
            </div>

            {/* Stats */}
            <div className="fc-thread-stats">
              <span className="fc-stat-comments">
                <HiOutlineChatBubbleOvalLeft />
                {t.commentCount}
              </span>
              {t.unreadCount > 0 && (
                <span className="fc-unread-badge" title={`${t.unreadCount} unread`}>
                  {t.unreadCount > 99 ? '99+' : t.unreadCount} new
                </span>
              )}
            </div>

            {/* Admin actions */}
            {isAdmin && (
              <div className="fc-admin-actions">
                <button className="btn-ghost" onClick={() => handlePin(t)}
                  title={t.isPinned ? 'Unpin' : 'Pin'}>
                  {t.isPinned ? 'Unpin' : 'Pin'}
                </button>
                <button className="btn-ghost" onClick={() => handleLock(t)}
                  title={t.isLocked ? 'Unlock' : 'Lock'}>
                  {t.isLocked ? 'Unlock' : 'Lock'}
                </button>
                <button className="btn-danger" onClick={() => handleDelete(t)}>Del</button>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* ── Pagination ── */}
      {totalPages > 1 && (
        <div className="fc-pagination">
          <button className="btn-ghost" disabled={page === 0} onClick={() => load(page - 1)}>
            <HiOutlineChevronLeft />
          </button>
          <span className="fc-page-info">Page {page + 1} / {totalPages}</span>
          <button className="btn-ghost" disabled={page >= totalPages - 1} onClick={() => load(page + 1)}>
            <HiOutlineChevronRight />
          </button>
        </div>
      )}

      {/* ── Create Thread Modal ── */}
      {showCreate && (
        <div className="modal-backdrop" onClick={() => setShowCreate(false)}>
          <div className="modal-box modal-box-lg" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">New Thread in {category?.name}</h2>
            <form onSubmit={handleCreate}>
              <div className="form-group">
                <label>Title</label>
                <input
                  value={form.title}
                  onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                  placeholder="Thread title"
                  required
                />
              </div>
              <div className="form-group">
                <label>Content</label>
                <RichTextEditor
                  value={form.body}
                  onChange={(v) => setForm((p) => ({ ...p, body: v }))}
                  placeholder="Write your post…"
                  minHeight="140px"
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Posting…' : 'Post Thread'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
