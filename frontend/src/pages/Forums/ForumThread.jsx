import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import {
  getForumThread,
  addForumComment,
  editForumComment,
  deleteForumComment,
  editForumThread,
  deleteForumThread,
  pinForumThread,
  lockForumThread,
} from '../../api/auth';
import {
  HiOutlineArrowLeft,
  HiOutlineLockClosed,
  HiOutlineTrash,
  HiOutlinePencil,
  HiOutlineChatBubbleOvalLeft,
} from 'react-icons/hi2';
import toast from 'react-hot-toast';
import RichTextEditor from '../../components/RichTextEditor/RichTextEditor';
import RichTextRenderer from '../../components/RichTextEditor/RichTextRenderer';
import './ForumThread.css';

function formatDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

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

export default function ForumThread() {
  const { threadId }       = useParams();
  const navigate           = useNavigate();
  const { user: authUser } = useAuth();
  const isAdmin            = authUser?.role === 'ADMIN';
  const bottomRef          = useRef(null);

  const [thread,  setThread]  = useState(null);
  const [loading, setLoading] = useState(true);
  const [body,    setBody]    = useState('');
  const [posting, setPosting] = useState(false);

  // Edit thread body
  const [editingThread, setEditingThread] = useState(false);
  const [threadBodyEdit, setThreadBodyEdit] = useState('');

  // Edit comment
  const [editingCommentId, setEditingCommentId] = useState(null);
  const [commentEditBody,  setCommentEditBody]  = useState('');

  const load = () => {
    setLoading(true);
    getForumThread(threadId)
      .then((r) => setThread(r.data))
      .catch(() => toast.error('Failed to load thread'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [threadId]); // eslint-disable-line

  /* ── Reply ── */
  const handleComment = async (e) => {
    e.preventDefault();
    if (!body.trim()) return;
    setPosting(true);
    try {
      await addForumComment(threadId, { body });
      setBody('');
      load();
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 300);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to post comment');
    } finally {
      setPosting(false);
    }
  };

  /* ── Edit thread body ── */
  const startEditThread = () => {
    setThreadBodyEdit(thread.body);
    setEditingThread(true);
  };
  const cancelEditThread = () => setEditingThread(false);
  const saveEditThread = async () => {
    if (!threadBodyEdit.trim()) return;
    try {
      await editForumThread(threadId, { body: threadBodyEdit });
      toast.success('Thread updated');
      setEditingThread(false);
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to update thread');
    }
  };

  /* ── Edit comment ── */
  const startEditComment = (c) => {
    setEditingCommentId(c.id);
    setCommentEditBody(c.body);
  };
  const cancelEditComment = () => { setEditingCommentId(null); setCommentEditBody(''); };
  const saveEditComment = async (commentId) => {
    if (!commentEditBody.trim()) return;
    try {
      await editForumComment(commentId, { body: commentEditBody });
      toast.success('Comment updated');
      cancelEditComment();
      load();
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to update comment');
    }
  };

  /* ── Delete comment ── */
  const handleDeleteComment = async (commentId) => {
    if (!window.confirm('Delete this comment?')) return;
    try {
      await deleteForumComment(commentId);
      toast.success('Comment deleted');
      load();
    } catch {
      toast.error('Failed to delete comment');
    }
  };

  /* ── Delete thread ── */
  const handleDeleteThread = async () => {
    if (!window.confirm(`Delete thread "${thread.title}"? This cannot be undone.`)) return;
    try {
      await deleteForumThread(threadId);
      toast.success('Thread deleted');
      navigate(`/forums/category/${thread.category.id}`);
    } catch {
      toast.error('Failed to delete thread');
    }
  };

  /* ── Pin / Lock ── */
  const handlePin = async () => {
    try {
      await pinForumThread(threadId, !thread.isPinned);
      toast.success(thread.isPinned ? 'Unpinned' : 'Pinned');
      load();
    } catch { toast.error('Failed'); }
  };
  const handleLock = async () => {
    try {
      await lockForumThread(threadId, !thread.isLocked);
      toast.success(thread.isLocked ? 'Unlocked' : 'Locked');
      load();
    } catch { toast.error('Failed'); }
  };

  if (loading && !thread) return <div className="ft-loading">Loading thread…</div>;
  if (!thread) return null;

  const isOwnThread = thread.authorId === authUser?.id;

  return (
    <div className="ft-page">
      {/* ── Back ── */}
      <button className="btn-ghost ft-back" onClick={() => navigate(`/forums/category/${thread.category.id}`)}>
        <HiOutlineArrowLeft /> {thread.category.name}
      </button>

      {/* ── Thread card ── */}
      <div className="ft-thread-card">
        <div className="ft-thread-header">
          <div className="ft-thread-badges">
            {thread.isPinned && <span className="badge-pin">Pinned</span>}
            {thread.isLocked && <span className="badge-locked"><HiOutlineLockClosed /> Locked</span>}
          </div>
          <h1 className="ft-thread-title">{thread.title}</h1>
          <div className="ft-thread-meta">
            <span>Posted by <strong>{thread.authorName}</strong></span>
            <span>·</span>
            <span title={formatDate(thread.createdAt)}>{timeAgo(thread.createdAt)}</span>
            <span>·</span>
            <span>
              <HiOutlineChatBubbleOvalLeft style={{ verticalAlign: 'middle' }} />{' '}
              {thread.commentCount} {thread.commentCount === 1 ? 'reply' : 'replies'}
            </span>
          </div>
        </div>

        {/* Thread body */}
        {editingThread ? (
          <div className="ft-inline-edit">
            <RichTextEditor value={threadBodyEdit} onChange={setThreadBodyEdit} minHeight="120px" />
            <div className="ft-inline-edit-actions">
              <button className="btn-primary" onClick={saveEditThread}>Save</button>
              <button className="btn-ghost"   onClick={cancelEditThread}>Cancel</button>
            </div>
          </div>
        ) : (
          <div className="ft-thread-body">
            <RichTextRenderer text={thread.body} />
          </div>
        )}

        {/* OP actions */}
        <div className="ft-admin-bar">
          {isOwnThread && !editingThread && (
            <button className="btn-ghost" onClick={startEditThread}>
              <HiOutlinePencil /> Edit
            </button>
          )}
          {isAdmin && (
            <>
              <button className="btn-ghost" onClick={handlePin}>
                {thread.isPinned ? 'Unpin' : 'Pin'}
              </button>
              <button className="btn-ghost" onClick={handleLock}>
                {thread.isLocked ? 'Unlock' : 'Lock'}
              </button>
              <button className="btn-danger" onClick={handleDeleteThread}>
                <HiOutlineTrash /> Delete Thread
              </button>
            </>
          )}
        </div>
      </div>

      {/* ── Comments ── */}
      <div className="ft-comments">
        <h2 className="ft-comments-heading">
          {thread.commentCount} {thread.commentCount === 1 ? 'Reply' : 'Replies'}
        </h2>

        {thread.comments.map((c, i) => (
          <div key={c.id} className={`ft-comment ${c.isOwnComment ? 'ft-comment-own' : ''}`}>
            <div className="ft-comment-avatar" title={c.authorName}>
              {c.authorName.charAt(0).toUpperCase()}
            </div>
            <div className="ft-comment-body-area">
              <div className="ft-comment-header">
                <strong className="ft-comment-author">{c.authorName}</strong>
                <span className="ft-comment-num">#{i + 1}</span>
                <span className="ft-comment-time" title={formatDate(c.createdAt)}>
                  {timeAgo(c.createdAt)}
                </span>
                <div className="ft-comment-actions">
                  {c.isOwnComment && editingCommentId !== c.id && (
                    <button
                      className="btn-ghost ft-comment-act"
                      onClick={() => startEditComment(c)}
                      title="Edit comment"
                    >
                      <HiOutlinePencil />
                    </button>
                  )}
                  {(c.isOwnComment || isAdmin) && editingCommentId !== c.id && (
                    <button
                      className="btn-danger ft-comment-act"
                      onClick={() => handleDeleteComment(c.id)}
                      title="Delete comment"
                    >
                      <HiOutlineTrash />
                    </button>
                  )}
                </div>
              </div>

              {editingCommentId === c.id ? (
                <div className="ft-inline-edit">
                  <RichTextEditor
                    value={commentEditBody}
                    onChange={setCommentEditBody}
                    minHeight="72px"
                  />
                  <div className="ft-inline-edit-actions">
                    <button className="btn-primary" onClick={() => saveEditComment(c.id)}>Save</button>
                    <button className="btn-ghost"   onClick={cancelEditComment}>Cancel</button>
                  </div>
                </div>
              ) : (
                <div className="ft-comment-text">
                  <RichTextRenderer text={c.body} />
                </div>
              )}
            </div>
          </div>
        ))}

        {thread.comments.length === 0 && (
          <p className="ft-no-comments">No replies yet. Be the first!</p>
        )}
      </div>

      {/* ── Reply box ── */}
      <div ref={bottomRef}>
        {thread.isLocked ? (
          <div className="ft-locked-notice">
            <HiOutlineLockClosed /> This thread is locked. No new replies allowed.
          </div>
        ) : (
          <form className="ft-reply-form" onSubmit={handleComment}>
            <h3 className="ft-reply-heading">Post a Reply</h3>
            <RichTextEditor
              value={body}
              onChange={setBody}
              placeholder="Write your reply…"
              minHeight="96px"
            />
            <div className="ft-reply-actions">
              <button type="submit" className="btn-primary" disabled={posting || !body.trim()}>
                {posting ? 'Posting…' : 'Post Reply'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}


function formatDate(dateStr) {
  if (!dateStr) return '';
  return new Date(dateStr).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

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

export default function ForumThread() {
  const { threadId }        = useParams();
  const navigate            = useNavigate();
  const { user: authUser }  = useAuth();
  const isAdmin             = authUser?.role === 'ADMIN';
  const bottomRef           = useRef(null);

  const [thread,   setThread]   = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [body,     setBody]     = useState('');
  const [posting,  setPosting]  = useState(false);

  const load = () => {
    setLoading(true);
    getForumThread(threadId)
      .then((r) => setThread(r.data))
      .catch(() => toast.error('Failed to load thread'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [threadId]); // eslint-disable-line

  const handleComment = async (e) => {
    e.preventDefault();
    if (!body.trim()) return;
    setPosting(true);
    try {
      await addForumComment(threadId, { body });
      setBody('');
      load();
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 300);
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to post comment');
    } finally {
      setPosting(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    if (!window.confirm('Delete this comment?')) return;
    try {
      await deleteForumComment(commentId);
      toast.success('Comment deleted');
      load();
    } catch {
      toast.error('Failed to delete comment');
    }
  };

  const handleDeleteThread = async () => {
    if (!window.confirm(`Delete thread "${thread.title}"? This cannot be undone.`)) return;
    try {
      await deleteForumThread(threadId);
      toast.success('Thread deleted');
      navigate(`/forums/category/${thread.category.id}`);
    } catch {
      toast.error('Failed to delete thread');
    }
  };

  const handlePin = async () => {
    try {
      await pinForumThread(threadId, !thread.isPinned);
      toast.success(thread.isPinned ? 'Unpinned' : 'Pinned');
      load();
    } catch {
      toast.error('Failed');
    }
  };

  const handleLock = async () => {
    try {
      await lockForumThread(threadId, !thread.isLocked);
      toast.success(thread.isLocked ? 'Unlocked' : 'Locked');
      load();
    } catch {
      toast.error('Failed');
    }
  };

  if (loading && !thread) return <div className="ft-loading">Loading thread…</div>;
  if (!thread) return null;

  return (
    <div className="ft-page">
      {/* ── Back ── */}
      <button
        className="btn-ghost ft-back"
        onClick={() => navigate(`/forums/category/${thread.category.id}`)}
      >
        <HiOutlineArrowLeft /> {thread.category.name}
      </button>

      {/* ── Thread title + body ── */}
      <div className="ft-thread-card">
        <div className="ft-thread-header">
          <div className="ft-thread-badges">
            {thread.isPinned && <span className="badge-pin">Pinned</span>}
            {thread.isLocked && (
              <span className="badge-locked">
                <HiOutlineLockClosed /> Locked
              </span>
            )}
          </div>
          <h1 className="ft-thread-title">{thread.title}</h1>
          <div className="ft-thread-meta">
            <span>Posted by <strong>{thread.authorName}</strong></span>
            <span>·</span>
            <span title={formatDate(thread.createdAt)}>{timeAgo(thread.createdAt)}</span>
            <span>·</span>
            <span>
              <HiOutlineChatBubbleOvalLeft style={{ verticalAlign: 'middle' }} />{' '}
              {thread.commentCount} {thread.commentCount === 1 ? 'reply' : 'replies'}
            </span>
          </div>
        </div>

        <div className="ft-thread-body">
          {thread.body.split('\n').map((line, i) => (
            <p key={i}>{line || <br />}</p>
          ))}
        </div>

        {/* Admin controls on OP */}
        {isAdmin && (
          <div className="ft-admin-bar">
            <button className="btn-ghost" onClick={handlePin}>
              {thread.isPinned ? 'Unpin' : 'Pin'}
            </button>
            <button className="btn-ghost" onClick={handleLock}>
              {thread.isLocked ? 'Unlock' : 'Lock'}
            </button>
            <button className="btn-danger" onClick={handleDeleteThread}>
              <HiOutlineTrash /> Delete Thread
            </button>
          </div>
        )}
      </div>

      {/* ── Comments ── */}
      <div className="ft-comments">
        <h2 className="ft-comments-heading">
          {thread.commentCount} {thread.commentCount === 1 ? 'Reply' : 'Replies'}
        </h2>

        {thread.comments.map((c, i) => (
          <div key={c.id} className={`ft-comment ${c.isOwnComment ? 'ft-comment-own' : ''}`}>
            <div className="ft-comment-avatar" title={c.authorName}>
              {c.authorName.charAt(0).toUpperCase()}
            </div>
            <div className="ft-comment-body-area">
              <div className="ft-comment-header">
                <strong className="ft-comment-author">{c.authorName}</strong>
                <span className="ft-comment-num">#{i + 1}</span>
                <span className="ft-comment-time" title={formatDate(c.createdAt)}>
                  {timeAgo(c.createdAt)}
                </span>
                {isAdmin && (
                  <button
                    className="btn-danger ft-comment-del"
                    onClick={() => handleDeleteComment(c.id)}
                    title="Delete comment"
                  >
                    <HiOutlineTrash />
                  </button>
                )}
              </div>
              <div className="ft-comment-text">
                {c.body.split('\n').map((line, j) => (
                  <p key={j}>{line || <br />}</p>
                ))}
              </div>
            </div>
          </div>
        ))}

        {thread.comments.length === 0 && (
          <p className="ft-no-comments">No replies yet. Be the first!</p>
        )}
      </div>

      {/* ── Reply box ── */}
      <div ref={bottomRef}>
        {thread.isLocked ? (
          <div className="ft-locked-notice">
            <HiOutlineLockClosed /> This thread is locked. No new replies allowed.
          </div>
        ) : (
          <form className="ft-reply-form" onSubmit={handleComment}>
            <h3 className="ft-reply-heading">Post a Reply</h3>
            <textarea
              className="ft-reply-input"
              rows={4}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Write your reply…"
              required
            />
            <div className="ft-reply-actions">
              <button type="submit" className="btn-primary" disabled={posting || !body.trim()}>
                {posting ? 'Posting…' : 'Post Reply'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
