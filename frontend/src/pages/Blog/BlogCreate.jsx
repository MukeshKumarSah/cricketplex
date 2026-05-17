import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { createBlog, editBlog, getBlog, uploadBlogImage } from '../../api/auth';
import RichTextEditor from '../../components/RichTextEditor/RichTextEditor';
import RichTextRenderer from '../../components/RichTextEditor/RichTextRenderer';
import { HiOutlineArrowLeft, HiOutlineEye, HiOutlinePencil } from 'react-icons/hi2';
import './BlogCreate.css';

export default function BlogCreate() {
  const { id }    = useParams(); // present when editing
  const navigate  = useNavigate();
  const { user, loading: authLoading } = useAuth();

  const isEdit = Boolean(id);
  const canWrite = user?.role === 'ADMIN' || Boolean(user?.isSupporter);

  const [title,       setTitle]       = useState('');
  const [description, setDescription] = useState('');
  const [body,        setBody]        = useState('');
  const [preview,     setPreview]     = useState(false);
  const [saving,      setSaving]      = useState(false);
  const [loading,     setLoading]     = useState(isEdit);

  // Redirect non-writers (wait for auth to fully load first)
  useEffect(() => {
    if (!authLoading && user && !canWrite) navigate('/blogs');
    if (!authLoading && !user) navigate('/login');
  }, [authLoading, user, canWrite, navigate]);

  // Load existing blog for edit
  useEffect(() => {
    if (!isEdit) return;
    getBlog(id)
      .then(r => {
        setTitle(r.data.title || '');
        setDescription(r.data.description || '');
        setBody(r.data.body || '');
      })
      .catch(() => navigate('/blogs'))
      .finally(() => setLoading(false));
  }, [id, isEdit, navigate]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!title.trim())       { alert('Title is required'); return; }
    if (!description.trim()) { alert('Description is required'); return; }
    setSaving(true);
    try {
      if (isEdit) {
        await editBlog(id, { title, description, body });
        navigate(`/blogs/${id}`);
      } else {
        const res = await createBlog({ title, description, body });
        navigate(`/blogs/${res.data.id}`);
      }
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to save blog');
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className="blog-loading">Loading…</div>;

  return (
    <div className="blog-create-page">
      <button className="blog-post-back" onClick={() => navigate(isEdit ? `/blogs/${id}` : '/blogs')}>
        <HiOutlineArrowLeft /> {isEdit ? 'Back to Blog' : 'Back to Blogs'}
      </button>

      <div className="blog-create-header">
        <h1 className="blog-create-title">{isEdit ? 'Edit Blog' : 'New Blog'}</h1>
        <button
          type="button"
          className="btn-secondary"
          onClick={() => setPreview(p => !p)}
        >
          {preview ? <HiOutlinePencil /> : <HiOutlineEye />}
          {preview ? 'Edit' : 'Preview'}
        </button>
      </div>

      {preview ? (
        <div className="blog-create-preview">
          <h2 className="blog-preview-title">{title || 'Untitled'}</h2>
          {description && <div className="blog-post-desc">{description}</div>}
          <hr className="blog-post-divider" />
          <RichTextRenderer text={body} />
        </div>
      ) : (
        <form className="blog-create-form" onSubmit={handleSubmit}>
          <div className="blog-form-group">
            <label>Title <span className="req">*</span></label>
            <input
              type="text"
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="Enter blog title…"
              maxLength={300}
              required
            />
          </div>

          <div className="blog-form-group">
            <label>
              Description <span className="req">*</span>
              <span className="blog-form-hint"> (shown as excerpt and in forum thread)</span>
            </label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="A short description of this blog post…"
              rows={3}
              required
            />
          </div>

          <div className="blog-form-group">
            <label>Content</label>
            <div className="blog-form-hint blog-layout-hint">
              Use <strong>⊞</strong> for gallery row, <strong>◧</strong> for image-left layout, <strong>◨</strong> for image-right layout.
            </div>
            <RichTextEditor
              value={body}
              onChange={setBody}
              placeholder="Write your blog content here…"
              minHeight="320px"
              uploadImageFn={uploadBlogImage}
              blogMode
            />
          </div>

          <div className="blog-form-actions">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate(isEdit ? `/blogs/${id}` : '/blogs')}
            >
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={saving}>
              {saving ? 'Saving…' : isEdit ? 'Save Changes' : 'Publish Blog'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
