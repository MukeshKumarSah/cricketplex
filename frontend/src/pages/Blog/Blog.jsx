import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getBlogs } from '../../api/auth';
import {
  HiOutlinePencilSquare,
  HiOutlineChevronLeft,
  HiOutlineChevronRight,
} from 'react-icons/hi2';
import './Blog.css';

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const utc = (dateStr.endsWith('Z') || /[+-]\d{2}:\d{2}$/.test(dateStr)) ? dateStr : dateStr + 'Z';
  const diff = (Date.now() - new Date(utc).getTime()) / 1000;
  if (diff < 60)    return 'just now';
  if (diff < 3600)  return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 2592000) return `${Math.floor(diff / 86400)}d ago`;
  return new Date(utc).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
}

function ReactionChips({ reactions }) {
  const entries = Object.entries(reactions || {}).filter(([, c]) => c > 0);
  if (entries.length === 0) return null;
  return (
    <div className="blog-card-reactions">
      {entries.map(([emoji, count]) => (
        <span key={emoji} className="blog-reaction-chip">{emoji} {count}</span>
      ))}
    </div>
  );
}

export default function Blog() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [data, setData]   = useState(null);
  const [page, setPage]   = useState(0);
  const [loading, setLoading] = useState(true);

  const canWrite = user?.role === 'ADMIN' || user?.isSupporter === true;

  // Mark blog page as visited — clears the sidebar new-blog badge
  useEffect(() => {
    localStorage.setItem('blogLastVisited', new Date().toISOString());
  }, []);

  useEffect(() => {
    setLoading(true);
    getBlogs(page)
      .then(r => setData(r.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [page]);

  return (
    <div className="blog-page">
      <div className="blog-page-header">
        <div className="blog-page-header-left">
          <span className="blog-page-icon">✍️</span>
          <div>
            <h1 className="blog-page-title">Blog</h1>
            <p className="blog-page-subtitle">Articles, updates and stories from the CricketPlex team</p>
          </div>
        </div>
        {canWrite && (
          <button className="btn-primary" onClick={() => navigate('/home/blogs/create')}>
            <HiOutlinePencilSquare /> New Blog
          </button>
        )}
      </div>

      {loading && <div className="blog-loading">Loading…</div>}

      {!loading && data && data.blogs.length === 0 && (
        <div className="blog-empty">No blogs published yet. Check back soon!</div>
      )}

      {!loading && data && data.blogs.length > 0 && (
        <>
          <div className="blog-list">
            {data.blogs.map(blog => (
              <div
                key={blog.id}
                className={`blog-card${blog.isPinned ? ' blog-card-pinned' : ''}`}
                onClick={() => navigate(`/home/blogs/${blog.id}`)}
              >
                <div className="blog-card-top">
                  <h2 className="blog-card-title">{blog.title}</h2>
                  {blog.isPinned && <span className="blog-pin-badge">📌 Pinned</span>}
                </div>
                <p className="blog-card-desc">{blog.description}</p>
                <div className="blog-card-footer">
                  <div className="blog-card-meta">
                    <div className="blog-author-avatar">
                      {(blog.authorName || '?')[0]}
                    </div>
                    <span>{blog.authorName}</span>
                    <span className="blog-post-dot">·</span>
                    <span>{timeAgo(blog.createdAt)}</span>
                  </div>
                  <ReactionChips reactions={blog.reactions} />
                </div>
              </div>
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="blog-pagination">
              <button
                className="blog-page-btn"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
              >
                <HiOutlineChevronLeft /> Prev
              </button>
              <span className="blog-page-info">
                Page {page + 1} of {data.totalPages}
              </span>
              <button
                className="blog-page-btn"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Next <HiOutlineChevronRight />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
