import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import axios from '../../api/axios';
import toast from 'react-hot-toast';
import { 
  HiOutlineCheckCircle, 
  HiOutlineXCircle, 
  HiOutlineClock,
  HiOutlinePencilSquare,
  HiOutlineTrash,
  HiOutlineChartBar
} from 'react-icons/hi2';
import './AdminCommentary.css';

export default function AdminCommentary() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('pending');
  const [loading, setLoading] = useState(false);
  const [submissions, setSubmissions] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectingId, setRejectingId] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [editText, setEditText] = useState('');

  useEffect(() => {
    loadSubmissions();
  }, [activeTab]);

  const loadSubmissions = async () => {
    setLoading(true);
    try {
      const res = await axios.get(`/admin/commentary/${activeTab}`);
      setSubmissions(res.data.submissions || []);
      setSelectedIds([]);
    } catch (error) {
      toast.error('Failed to load submissions');
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await axios.post(`/admin/commentary/${id}/approve`);
      toast.success('Commentary approved');
      loadSubmissions();
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to approve');
    }
  };

  const handleReject = async (id, reason) => {
    if (!reason || reason.trim().length < 5) {
      toast.error('Please provide a rejection reason (min 5 characters)');
      return;
    }

    try {
      await axios.post(`/admin/commentary/${id}/reject`, { reason });
      toast.success('Commentary rejected');
      loadSubmissions();
      setShowRejectModal(false);
      setRejectReason('');
      setRejectingId(null);
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to reject');
    }
  };

  const handleBulkApprove = async () => {
    if (selectedIds.length === 0) {
      toast.error('No submissions selected');
      return;
    }

    try {
      const res = await axios.post('/admin/commentary/bulk-approve', {
        ids: selectedIds
      });
      toast.success(`Approved ${res.data.approved} commentaries`);
      if (res.data.failed > 0) {
        toast.error(`${res.data.failed} failed`);
      }
      loadSubmissions();
    } catch (error) {
      toast.error('Bulk approve failed');
    }
  };

  const handleBulkReject = async () => {
    if (selectedIds.length === 0) {
      toast.error('No submissions selected');
      return;
    }

    if (!rejectReason || rejectReason.trim().length < 5) {
      toast.error('Please provide a rejection reason (min 5 characters)');
      return;
    }

    try {
      const res = await axios.post('/admin/commentary/bulk-reject', {
        ids: selectedIds,
        reason: rejectReason
      });
      toast.success(`Rejected ${res.data.rejected} commentaries`);
      if (res.data.failed > 0) {
        toast.error(`${res.data.failed} failed`);
      }
      loadSubmissions();
      setShowRejectModal(false);
      setRejectReason('');
    } catch (error) {
      toast.error('Bulk reject failed');
    }
  };

  const handleEdit = async (id, newText) => {
    try {
      const res = await axios.put(`/admin/commentary/${id}`, {
        commentaryText: newText
      });
      
      if (res.data.success) {
        toast.success('Commentary updated');
        loadSubmissions();
        setEditingId(null);
        setEditText('');
      } else {
        toast.error(res.data.errors?.join(', ') || 'Failed to update');
      }
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to update');
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this commentary permanently?')) return;
    
    try {
      await axios.delete(`/admin/commentary/${id}`);
      toast.success('Commentary deleted');
      loadSubmissions();
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to delete');
    }
  };

  const toggleSelection = (id) => {
    setSelectedIds(prev => 
      prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (selectedIds.length === filteredSubmissions.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(filteredSubmissions.map(s => s.id));
    }
  };

  const filteredSubmissions = submissions.filter(s =>
    s.commentaryText?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    s.username?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Check if user is admin
  if (!user || user.role !== 'ADMIN') {
    return (
      <div className="admin-commentary-container">
        <div className="access-denied">
          <h1>Access Denied</h1>
          <p>This page is only accessible to administrators.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-commentary-container">
      <div className="admin-header">
        <h1>Commentary Admin Panel</h1>
      </div>

      <div className="admin-tabs">
        <button 
          className={`admin-tab ${activeTab === 'pending' ? 'active' : ''}`}
          onClick={() => setActiveTab('pending')}
        >
          <HiOutlineClock size={18} />
          Pending
          <span className="badge">{submissions.length}</span>
        </button>
        <button 
          className={`admin-tab ${activeTab === 'approved' ? 'active' : ''}`}
          onClick={() => setActiveTab('approved')}
        >
          <HiOutlineCheckCircle size={18} />
          Approved
          <span className="badge">{submissions.length}</span>
        </button>
        <button 
          className={`admin-tab ${activeTab === 'rejected' ? 'active' : ''}`}
          onClick={() => setActiveTab('rejected')}
        >
          <HiOutlineXCircle size={18} />
          Rejected
          <span className="badge">{submissions.length}</span>
        </button>
        <button 
          className={`admin-tab ${activeTab === 'stats' ? 'active' : ''}`}
          onClick={() => setActiveTab('stats')}
        >
          <HiOutlineChartBar size={18} />
          Statistics
        </button>
      </div>

      {activeTab !== 'stats' && (
        <>
          <div className="admin-actions">
            <input
              type="text"
              placeholder="Search commentary or username..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="search-input"
            />

            {activeTab === 'pending' && selectedIds.length > 0 && (
              <div className="bulk-actions">
                <button 
                  className="btn btn-success btn-sm"
                  onClick={handleBulkApprove}
                >
                  Approve Selected ({selectedIds.length})
                </button>
                <button 
                  className="btn btn-danger btn-sm"
                  onClick={() => setShowRejectModal(true)}
                >
                  Reject Selected ({selectedIds.length})
                </button>
              </div>
            )}

            {activeTab === 'pending' && filteredSubmissions.length > 0 && (
              <button 
                className="btn btn-secondary btn-sm"
                onClick={toggleSelectAll}
              >
                {selectedIds.length === filteredSubmissions.length ? 'Deselect All' : 'Select All'}
              </button>
            )}
          </div>

          {loading ? (
            <div className="loading">
              <div className="spinner"></div>
              <p>Loading submissions...</p>
            </div>
          ) : filteredSubmissions.length > 0 ? (
            <div className="admin-submissions-list">
              {filteredSubmissions.map(sub => (
                <div key={sub.id} className="admin-submission-card">
                  {activeTab === 'pending' && (
                    <div className="submission-checkbox">
                      <input
                        type="checkbox"
                        checked={selectedIds.includes(sub.id)}
                        onChange={() => toggleSelection(sub.id)}
                      />
                    </div>
                  )}

                  <div className="submission-content">
                    <div className="submission-user">
                      <strong>{sub.username}</strong> ({sub.teamName})
                    </div>

                    {editingId === sub.id ? (
                      <div className="submission-edit">
                        <textarea
                          value={editText}
                          onChange={(e) => setEditText(e.target.value)}
                          rows="3"
                          className="edit-textarea"
                        />
                        <div className="edit-actions">
                          <button 
                            className="btn btn-sm btn-secondary"
                            onClick={() => {
                              setEditingId(null);
                              setEditText('');
                            }}
                          >
                            Cancel
                          </button>
                          <button 
                            className="btn btn-sm btn-primary"
                            onClick={() => handleEdit(sub.id, editText)}
                          >
                            Save
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="submission-text">
                        {sub.commentaryText}
                      </div>
                    )}

                    <div className="submission-meta">
                      <span><strong>Format:</strong> {sub.matchFormat}</span>
                      <span><strong>Phase:</strong> {sub.phase}</span>
                      <span><strong>Bowler:</strong> {sub.bowlerType}</span>
                      <span><strong>Event:</strong> {sub.eventType}</span>
                      <span><strong>Used:</strong> {sub.timesUsed} times</span>
                      <span><strong>Created:</strong> {new Date(sub.createdAt).toLocaleDateString()}</span>
                    </div>

                    {sub.adminNotes && (
                      <div className="admin-notes">
                        <strong>Rejection Reason:</strong>
                        <p>{sub.adminNotes}</p>
                      </div>
                    )}

                    <div className="submission-actions">
                      {activeTab === 'pending' && (
                        <>
                          <button 
                            className="btn btn-sm btn-success"
                            onClick={() => handleApprove(sub.id)}
                          >
                            <HiOutlineCheckCircle size={16} />
                            Approve
                          </button>
                          <button 
                            className="btn btn-sm btn-danger"
                            onClick={() => {
                              setRejectingId(sub.id);
                              setShowRejectModal(true);
                            }}
                          >
                            <HiOutlineXCircle size={16} />
                            Reject
                          </button>
                        </>
                      )}
                      
                      <button 
                        className="btn btn-sm btn-secondary"
                        onClick={() => {
                          setEditingId(sub.id);
                          setEditText(sub.commentaryText);
                        }}
                      >
                        <HiOutlinePencilSquare size={16} />
                        Edit
                      </button>
                      
                      <button 
                        className="btn btn-sm btn-danger"
                        onClick={() => handleDelete(sub.id)}
                      >
                        <HiOutlineTrash size={16} />
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">📭</div>
              <h3>No {activeTab} submissions</h3>
              <p>{searchTerm ? 'Try a different search term' : 'All caught up!'}</p>
            </div>
          )}
        </>
      )}

      {activeTab === 'stats' && (
        <div className="stats-section">
          <h2>Statistics (Coming Soon)</h2>
          <p>This section will show:</p>
          <ul>
            <li>Coverage gaps by event type</li>
            <li>Top contributors</li>
            <li>Most used commentaries</li>
            <li>Commentary usage trends</li>
          </ul>
        </div>
      )}

      {/* Reject Modal */}
      {showRejectModal && (
        <div className="modal-overlay" onClick={() => setShowRejectModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>Reject Commentary</h3>
            <p>
              {rejectingId 
                ? 'Please provide a reason for rejecting this commentary:' 
                : `Rejecting ${selectedIds.length} selected commentaries`}
            </p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Enter rejection reason (min 5 characters)..."
              rows="4"
              className="reject-reason-textarea"
            />
            <div className="modal-actions">
              <button 
                className="btn btn-secondary"
                onClick={() => {
                  setShowRejectModal(false);
                  setRejectReason('');
                  setRejectingId(null);
                }}
              >
                Cancel
              </button>
              <button 
                className="btn btn-danger"
                onClick={() => {
                  if (rejectingId) {
                    handleReject(rejectingId, rejectReason);
                  } else {
                    handleBulkReject();
                  }
                }}
                disabled={!rejectReason || rejectReason.trim().length < 5}
              >
                Confirm Reject
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
