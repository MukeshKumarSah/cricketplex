import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getBlog, reactToBlog, pinBlog, deleteBlog } from '../../api/auth';
import RichTextRenderer from '../../components/RichTextEditor/RichTextRenderer';
import {
  HiOutlineArrowLeft,
  HiOutlinePencil,
  HiOutlineTrash,
  HiOutlineArrowTopRightOnSquare,
} from 'react-icons/hi2';
import './Blog.css';

const REACTIONS = ['❤️', '👍', '👎', '🔥', '💯', '😂', '🎉', '👀', '🙏'];

function formatDate(dateStr) {
  if (!dateStr) return '';
  const utc = (dateStr.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(dateStr)) ? dateStr : dateStr + 'Z';
  return new Date(utc).toLocaleDateString('en-US', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC',
  });
}

export default function BlogPost() {
  const { id }     = useParams();
  const navigate   = useNavigate();
  const { user }   = useAuth();
  const [blog, setBlog]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [reacting, setReacting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const isAdmin   = user?.role === 'ADMIN';
  const canWrite  = isAdmin || user?.isSupporter === true;
  const isAuthor  = blog && user && blog.authorId === user.id;
  const canEdit   = isAdmin || isAuthor;
  const canDelete = isAdmin || isAuthor;

  useEffect(() => {
    setLoading(true);
    getBlog(id)
      .then(r => setBlog(r.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [id]);

  async function handleReact(emoji) {
    if (reacting) return;
    setReacting(true);
    try {
      const res = await reactToBlog(id, emoji);
      setBlog(prev => ({ ...prev, reactions: res.data.reactions, myReactions: res.data.myReactions }));
    } catch (e) {
      console.error(e);
    } finally {
      setReacting(false);
    }
  }

  async function handlePin() {
    try {
      await pinBlog(id, !blog.isPinned);
      setBlog(prev => ({ ...prev, isPinned: !prev.isPinned }));
    } catch (e) {
      alert(e.response?.data?.error || 'Failed to pin blog');
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this blog? This cannot be undone.')) return;
    setDeleting(true);
    try {
      await deleteBlog(id);
      navigate('/blogs');
    } catch (e) {
      alert(e.response?.data?.error || 'Failed to delete blog');
      setDeleting(false);
    }
  }

  if (loading) return <div className="blog-loading">Loading…</div>;
  if (!blog)   return <div className="blog-loading">Blog not found.</div>;

  return (
    <div className="blog-post-page">
      {/* Back */}
      <button className="blog-post-back" onClick={() => navigate('/blogs')}>
        <HiOutlineArrowLeft /> Back to Blog
      </button>

      {/* Header */}
      <div className="blog-post-header">
        <h1 className="blog-post-title">{blog.title}</h1>
        <div className="blog-post-meta">
          <div className="blog-author-avatar">{(blog.authorName || '?')[0]}</div>
          <span>{blog.authorName}</span>
          <span className="blog-post-dot">·</span>
          <span>{formatDate(blog.createdAt)}</span>
          {blog.isPinned && (
            <span className="blog-post-pin-badge">📌 Pinned</span>
          )}
        </div>
      </div>

      {/* Admin / author actions */}
      {(canEdit || canDelete || isAdmin) && (
        <div className="blog-post-admin-row">
          {canEdit && (
            <button
              className="btn-ghost"
              onClick={() => navigate(`/blogs/${id}/edit`)}
            >
              <HiOutlinePencil /> Edit
            </button>
          )}
          {isAdmin && (
            <button className="btn-ghost" onClick={handlePin}>
              {blog.isPinned ? '📌 Unpin' : '📌 Pin'}
            </button>
          )}
          {canDelete && (
            <button className="btn-danger" onClick={handleDelete} disabled={deleting}>
              <HiOutlineTrash /> {deleting ? 'Deleting…' : 'Delete'}
            </button>
          )}
        </div>
      )}

      {/* Description (styled quote) */}
      <div className="blog-post-desc">{blog.description}</div>

      <hr className="blog-post-divider" />

      {/* Body */}
      <div className="blog-post-body">
        <RichTextRenderer text={blog.body} />
      </div>

      {/* Reactions */}
      <div className="blog-reactions-section">
        <div className="blog-reactions-label">React</div>
        <div className="blog-reactions-row">
          {REACTIONS.map(emoji => {
            const count = blog.reactions?.[emoji] || 0;
            const active = blog.myReactions?.includes(emoji);
            return (
              <button
                key={emoji}
                className={`blog-react-btn${active ? ' active' : ''}`}
                onClick={() => handleReact(emoji)}
                disabled={reacting}
              >
                {emoji}
                {count > 0 && <span className="blog-react-count">{count}</span>}
              </button>
            );
          })}
        </div>
      </div>

      {/* Forum thread link */}
      {blog.forumThreadId && (
        <div className="blog-forum-link">
          <span className="blog-forum-link-text">💬 Discuss this blog in the forum</span>
          <button
            className="blog-forum-link-btn"
            onClick={() => navigate(`/home/forums/thread/${blog.forumThreadId}`)}
          >
            Open thread <HiOutlineArrowTopRightOnSquare />
          </button>
        </div>
      )}
    </div>
  );
}
