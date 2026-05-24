import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import axios from '../../api/axios';
import toast from 'react-hot-toast';
import { 
  HiOutlinePaperAirplane, 
  HiOutlineDocumentText, 
  HiOutlineCloudArrowUp,
  HiOutlineCheckCircle,
  HiOutlineXCircle,
  HiOutlineClock,
  HiOutlinePencilSquare,
  HiOutlineTrash,
  HiOutlineArrowDownTray
} from 'react-icons/hi2';
import './ManageCommentary.css';

export default function ManageCommentary() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('submit');
  const [loading, setLoading] = useState(false);
  
  // Submission form state
  const [formData, setFormData] = useState({
    commentaryText: '',
    matchFormat: 'all',
    phase: 'all',
    bowlerType: 'ALL',
    eventType: '0',
    wicketSituation: '',
    extraTags: []
  });
  
  // Submissions list state
  const [submissions, setSubmissions] = useState(null);
  const [filter, setFilter] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');
  
  // Excel import state
  const [importStep, setImportStep] = useState(1);
  const [importFile, setImportFile] = useState(null);
  const [validationResults, setValidationResults] = useState(null);
  
  // Filter options
  const [filterOptions, setFilterOptions] = useState(null);
  
  const [alert, setAlert] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const activeTeamId = user?.activeTeamId || user?.teamId || null;

  useEffect(() => {
    loadFilterOptions();
    if (activeTab === 'my-submissions') {
      loadMySubmissions();
    }
  }, [activeTab]);

  const loadFilterOptions = async () => {
    try {
      const res = await axios.get('/commentary/filter-options');
      setFilterOptions(res.data);
      console.log('Filter options loaded:', res.data);
    } catch (error) {
      console.error('Failed to load filter options:', error);
      toast.error('Failed to load form options. Using defaults.');
      // Set comprehensive defaults if API fails
      setFilterOptions({
        matchFormats: ['T20', 'ODI', 'FC', 'all'],
        phases: ['powerplay', 'middle', 'death', 'all'],
        bowlerTypes: ['F', 'FM', 'MF', 'M', 'FS', 'WS', 'LAP', 'PACE', 'SPINNER', 'ALL'],
        eventTypes: [
          '0', '1', '2', '3', '4', '5', '6',
          '1LB', '2LB', '3LB', '4LB',
          '1BYE', '2BYE', '3BYE', '4BYE',
          '1WD', '2WD', '3WD', '4WD', '5WD', '6WD', '7WD',
          '1NB', '2NB', '3NB', '4NB', '5NB', '6NB', '7NB',
          'BOWLED', 'CAUGHT', 'CAUGHT_BEHIND', 'LBW', 'RUN_OUT_0', 'RUN_OUT_1', 'RUN_OUT', 'STUMPED', 'HIT_WICKET', 'CAUGHT_AND_BOWLED'
        ],
        wicketSituations: ['run_out_striker', 'run_out_non_striker'],
        extraTags: [
          'catch_dropped', 'great_fielding', 'misfield',
          'free_hit',
          'strike_farming_strong_early', 'strike_farming_strong_late',
          'strike_farming_weak_early', 'strike_farming_weak_late',
          'strike_farming',
          'milestone_3w_haul', 'milestone_5w_haul',
          'milestone_50', 'milestone_100', 'milestone_150', 'milestone_200',
          'partnership_50', 'partnership_100', 'partnership_150', 'partnership_200', 'partnership_250'
        ]
      });
    }
  };

  const bowlerTypeLabels = {
    F: 'F - Fast',
    FM: 'FM - Fast Medium',
    MF: 'MF - Medium Fast',
    M: 'M - Medium',
    FS: 'FS - Finger Spinner',
    WS: 'WS - Wrist Spinner',
    LAP: 'LAP - Left-arm pace',
    PACE: 'PACE - All seamers',
    SPINNER: 'SPINNER - Finger + Wrist spin',
    ALL: 'ALL - Any bowler type'
  };

  const phaseLabels = {
    powerplay: 'Powerplay',
    middle: 'Middle',
    death: 'Death',
    all: 'All phases'
  };

  const matchFormatLabels = {
    T20: 'T20',
    ODI: 'ODI',
    FC: 'FC',
    all: 'All formats'
  };

  const eventTypeLabels = {
    '0': '0 - Dot Ball',
    '1': '1 - Single',
    '2': '2 - Two Runs',
    '3': '3 - Three Runs',
    '4': '4 - Four',
    '5': '5 - Five Runs',
    '6': '6 - Six',
    '1LB': '1LB - Leg Bye 1',
    '2LB': '2LB - Leg Bye 2',
    '3LB': '3LB - Leg Bye 3',
    '4LB': '4LB - Leg Bye 4',
    '1BYE': '1BYE - Bye 1',
    '2BYE': '2BYE - Bye 2',
    '3BYE': '3BYE - Bye 3',
    '4BYE': '4BYE - Bye 4',
    '1WD': '1WD - Wide 1',
    '2WD': '2WD - Wide 2',
    '3WD': '3WD - Wide 3',
    '4WD': '4WD - Wide 4',
    '5WD': '5WD - Wide 5',
    '6WD': '6WD - Wide 6',
    '7WD': '7WD - Wide 7',
    '1NB': '1NB - No Ball 1',
    '2NB': '2NB - No Ball 2',
    '3NB': '3NB - No Ball 3',
    '4NB': '4NB - No Ball 4',
    '5NB': '5NB - No Ball 5',
    '6NB': '6NB - No Ball 6',
    '7NB': '7NB - No Ball 7',
    BOWLED: 'BOWLED',
    CAUGHT: 'CAUGHT',
    CAUGHT_BEHIND: 'CAUGHT_BEHIND - Keeper catch',
    LBW: 'LBW',
    RUN_OUT_0: 'RUN_OUT_0 - Run out (0 run)',
    RUN_OUT_1: 'RUN_OUT_1 - Run out (1 run)',
    RUN_OUT: 'RUN_OUT - Generic run out',
    STUMPED: 'STUMPED',
    HIT_WICKET: 'HIT_WICKET',
    CAUGHT_AND_BOWLED: 'CAUGHT_AND_BOWLED'
  };

  const extraTagLabels = {
    catch_dropped: 'Catch dropped',
    great_fielding: 'Great fielding',
    misfield: 'Misfield',
    free_hit: 'Free hit',
    strike_farming_strong_early: 'Strike farming: strong batter, first 4 balls',
    strike_farming_strong_late: 'Strike farming: strong batter, last 2 balls',
    strike_farming_weak_early: 'Strike farming: weak batter, first 5 balls',
    strike_farming_weak_late: 'Strike farming: weak batter, last ball',
    strike_farming: 'Strike farming (generic)',
    milestone_3w_haul: 'Milestone: 3W haul',
    milestone_5w_haul: 'Milestone: 5W haul',
    milestone_50: 'Milestone: 50',
    milestone_100: 'Milestone: 100',
    milestone_150: 'Milestone: 150',
    milestone_200: 'Milestone: 200',
    partnership_50: 'Partnership: 50',
    partnership_100: 'Partnership: 100',
    partnership_150: 'Partnership: 150',
    partnership_200: 'Partnership: 200',
    partnership_250: 'Partnership: 250'
  };

  const wicketSituationLabels = {
    run_out_striker: 'Run out - striker',
    run_out_non_striker: 'Run out - non-striker'
  };

  const isRunOutEvent = formData.eventType === 'RUN_OUT' ||
    formData.eventType === 'RUN_OUT_0' ||
    formData.eventType === 'RUN_OUT_1';

  const loadMySubmissions = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/commentary/my-submissions');
      setSubmissions(res.data);
    } catch (error) {
      toast.error('Failed to load submissions');
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => {
      const next = { ...prev, [name]: value };
      if (name === 'eventType' && !value.startsWith('RUN_OUT')) {
        next.wicketSituation = '';
      }
      return next;
    });
  };

  const handleTagToggle = (tag) => {
    setFormData(prev => ({
      ...prev,
      extraTags: prev.extraTags.includes(tag) ? [] : [tag]
    }));
  };

  const insertPlaceholder = (placeholder) => {
    const textarea = document.querySelector('textarea[name="commentaryText"]');
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = formData.commentaryText;
    const before = text.substring(0, start);
    const after = text.substring(end);
    const newText = before + `[${placeholder}]` + after;
    
    setFormData(prev => ({ ...prev, commentaryText: newText }));
    
    // Set cursor position after inserted placeholder
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(start + placeholder.length + 2, start + placeholder.length + 2);
    }, 0);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!activeTeamId) {
      toast.error('No active team found. Please complete team setup first.');
      return;
    }

    setAlert(null);
    setLoading(true);

    try {
      const res = await axios.post('/commentary/submit', {
        ...formData,
        teamId: activeTeamId
      });

      if (res.data.success) {
        toast.success(res.data.message);
        
        // Show warnings if any
        if (res.data.warnings && res.data.warnings.length > 0) {
          setAlert({ type: 'warning', messages: res.data.warnings });
        }
        
        // Show similar commentary if found
        if (res.data.similarCommentary && res.data.similarCommentary.length > 0) {
          setAlert({ 
            type: 'info', 
            message: 'Similar commentary found',
            similar: res.data.similarCommentary 
          });
        }
        
        // Reset form
        setFormData({
          commentaryText: '',
          matchFormat: 'all',
          phase: 'all',
          bowlerType: 'ALL',
          eventType: '0',
          wicketSituation: '',
          extraTags: []
        });
      } else {
        setAlert({ type: 'error', messages: res.data.errors });
      }
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to submit commentary');
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = async (id, newText) => {
    try {
      const res = await axios.put(`/commentary/${id}`, {
        commentaryText: newText
      });
      
      if (res.data.success) {
        toast.success('Commentary updated');
        loadMySubmissions();
        setEditingId(null);
      } else {
        toast.error(res.data.errors?.join(', ') || 'Failed to update');
      }
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to update commentary');
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this commentary?')) return;
    
    try {
      await axios.delete(`/commentary/${id}`);
      toast.success('Commentary deleted');
      loadMySubmissions();
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to delete commentary');
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file && file.name.endsWith('.xlsx')) {
      setImportFile(file);
      setImportStep(2);
    } else {
      toast.error('Please select a .xlsx file');
    }
  };

  const handleValidate = async () => {
    if (!importFile) return;
    
    setLoading(true);
    const formData = new FormData();
    formData.append('file', importFile);

    try {
      const res = await axios.post('/commentary/import/validate', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      if (res.data.success) {
        setValidationResults(res.data);
        setImportStep(3);
      } else {
        toast.error(res.data.error);
      }
    } catch (error) {
      toast.error(error.response?.data?.error || 'Validation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!validationResults) return;

    if (!activeTeamId) {
      toast.error('No active team found. Please complete team setup first.');
      return;
    }
    
    setLoading(true);
    try {
      const res = await axios.post('/commentary/import/confirm', {
        teamId: activeTeamId,
        rows: [...validationResults.validRows, ...validationResults.warningRows]
      });

      if (res.data.success) {
        toast.success(`Imported ${res.data.imported} commentaries`);
        if (res.data.failed > 0) {
          toast.error(`${res.data.failed} failed to import`);
        }
        
        // Reset import
        setImportFile(null);
        setValidationResults(null);
        setImportStep(1);
        
        // Refresh submissions
        loadMySubmissions();
      }
    } catch (error) {
      toast.error(error.response?.data?.error || 'Import failed');
    } finally {
      setLoading(false);
    }
  };

  const downloadTemplate = async () => {
    try {
      const res = await axios.get('/commentary/import/template', {
        responseType: 'blob'
      });
      
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'commentary_template.xlsx');
      document.body.appendChild(link);
      link.click();
      link.remove();
      
      toast.success('Template downloaded');
    } catch (error) {
      toast.error('Failed to download template');
    }
  };

  const placeholders = [
    'batsman', 'non_striker', 'bowler', 'fielder', 'keeper',
    'batting_team', 'bowling_team', 'runs', 'score', 'wickets', 'overs'
  ];

  const charCount = formData.commentaryText.length;
  const charLimit = 500;
  const charWarning = charCount > 400;
  const charError = charCount > charLimit;

  return (
    <div className="commentary-container">
      <div className="commentary-header">
        <h1>Manage Commentary</h1>
        <div className="header-actions">
          {user.role === 'ADMIN' && (
            <button 
              className="btn btn-secondary"
              onClick={() => window.location.href = '/admin/commentary'}
            >
              Admin Panel
            </button>
          )}
        </div>
      </div>

      <div className="commentary-tabs">
        <button 
          className={`commentary-tab ${activeTab === 'submit' ? 'active' : ''}`}
          onClick={() => setActiveTab('submit')}
        >
          <HiOutlinePaperAirplane size={18} />
          Submit Commentary
        </button>
        <button 
          className={`commentary-tab ${activeTab === 'my-submissions' ? 'active' : ''}`}
          onClick={() => setActiveTab('my-submissions')}
        >
          <HiOutlineDocumentText size={18} />
          My Submissions
          {submissions && (
            <span className="badge">{submissions.total || 0}</span>
          )}
        </button>
        <button 
          className={`commentary-tab ${activeTab === 'import' ? 'active' : ''}`}
          onClick={() => setActiveTab('import')}
        >
          <HiOutlineCloudArrowUp size={18} />
          Excel Import
        </button>
      </div>

      {activeTab === 'submit' && (
        <div className="submit-form">
          <h2>Submit New Commentary</h2>
          
          {alert && (
            <div className={`alert alert-${alert.type}`}>
              <div>
                {alert.message && <p>{alert.message}</p>}
                {alert.messages && (
                  <ul>
                    {alert.messages.map((msg, idx) => <li key={idx}>{msg}</li>)}
                  </ul>
                )}
                {alert.similar && (
                  <div className="similar-commentary">
                    <strong>Similar commentary found:</strong>
                    {alert.similar.map((s, idx) => (
                      <div key={idx} className="similar-item">
                        "{s.text}" - by {s.author} ({s.status})
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <button className="alert-close" onClick={() => setAlert(null)}>×</button>
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-group required">
              <label>Commentary Text</label>
              <textarea
                name="commentaryText"
                value={formData.commentaryText}
                onChange={handleInputChange}
                placeholder="Enter your commentary with placeholders like [batsman], [bowler], [runs]..."
                required
                minLength={10}
                maxLength={500}
              />
              <div className={`char-count ${charWarning ? 'warning' : ''} ${charError ? 'error' : ''}`}>
                {charCount} / {charLimit} characters
              </div>
            </div>

            <div className="placeholder-helper">
              <h4>📝 Insert Placeholders</h4>
              <p>Click a placeholder to insert it at cursor position:</p>
              <div className="placeholder-tags">
                {placeholders.map(ph => (
                  <span 
                    key={ph} 
                    className="placeholder-tag"
                    onClick={() => insertPlaceholder(ph)}
                    title={`Insert [${ph}]`}
                  >
                    [{ph}]
                  </span>
                ))}
              </div>
            </div>

            <div className="form-grid">
              <div className="form-group required">
                <label>Match Format</label>
                <select name="matchFormat" value={formData.matchFormat} onChange={handleInputChange} required>
                  {!filterOptions ? (
                    <>
                      <option value="T20">T20</option>
                      <option value="ODI">ODI</option>
                      <option value="FC">FC</option>
                      <option value="all">All formats</option>
                    </>
                  ) : (
                    filterOptions.matchFormats?.map(fmt => (
                      <option key={fmt} value={fmt}>{matchFormatLabels[fmt] || fmt}</option>
                    ))
                  )}
                </select>
              </div>

              <div className="form-group required">
                <label>Phase</label>
                <select name="phase" value={formData.phase} onChange={handleInputChange} required>
                  {!filterOptions ? (
                    <>
                      <option value="powerplay">Powerplay</option>
                      <option value="middle">Middle</option>
                      <option value="death">Death</option>
                      <option value="all">All phases</option>
                    </>
                  ) : (
                    filterOptions.phases?.map(p => (
                      <option key={p} value={p}>{phaseLabels[p] || p}</option>
                    ))
                  )}
                </select>
              </div>

              <div className="form-group required">
                <label>Bowler Type</label>
                <select name="bowlerType" value={formData.bowlerType} onChange={handleInputChange} required>
                  {!filterOptions ? (
                    <>
                      <option value="F">F - Fast</option>
                      <option value="FM">FM - Fast Medium</option>
                      <option value="MF">MF - Medium Fast</option>
                      <option value="M">M - Medium</option>
                      <option value="FS">FS - Finger Spinner</option>
                      <option value="WS">WS - Wrist Spinner</option>
                      <option value="PACE">PACE - All seamers</option>
                      <option value="SPINNER">SPINNER - Finger + Wrist spin</option>
                      <option value="ALL">ALL - Any bowler type</option>
                    </>
                  ) : (
                    filterOptions.bowlerTypes?.map(bt => (
                      <option key={bt} value={bt}>{bowlerTypeLabels[bt] || bt}</option>
                    ))
                  )}
                </select>
              </div>

              <div className="form-group required">
                <label>Event Type</label>
                <select name="eventType" value={formData.eventType} onChange={handleInputChange} required>
                  {!filterOptions ? (
                    <>
                      <option value="0">Dot Ball</option>
                      <option value="1">Single</option>
                      <option value="2">Two Runs</option>
                      <option value="3">Three Runs</option>
                      <option value="4">Four</option>
                      <option value="5">Five Runs</option>
                      <option value="6">Six</option>
                      <option value="1BYE">Bye 1</option>
                      <option value="2BYE">Bye 2</option>
                      <option value="3BYE">Bye 3</option>
                      <option value="4BYE">Bye 4</option>
                      <option value="RUN_OUT_0">Run Out (0 run)</option>
                      <option value="RUN_OUT_1">Run Out (1 run)</option>
                      <option value="RUN_OUT">Run Out (generic)</option>
                      <option value="BOWLED">Bowled</option>
                      <option value="CAUGHT">Caught</option>
                      <option value="CAUGHT_BEHIND">Keeper Catch (Caught Behind)</option>
                      <option value="LBW">LBW</option>
                      <option value="STUMPED">Stumped</option>
                    </>
                  ) : (
                    filterOptions.eventTypes?.map(et => (
                      <option key={et} value={et}>{eventTypeLabels[et] || et}</option>
                    ))
                  )}
                </select>
              </div>

              {isRunOutEvent && (
                <div className="form-group required">
                  <label>Run-out Wicket Situation</label>
                  <select
                    name="wicketSituation"
                    value={formData.wicketSituation}
                    onChange={handleInputChange}
                    required
                  >
                    <option value="">Select who is out</option>
                    {(filterOptions?.wicketSituations || ['run_out_striker', 'run_out_non_striker']).map(ws => (
                      <option key={ws} value={ws}>{wicketSituationLabels[ws] || ws}</option>
                    ))}
                  </select>
                  <small>Required for run-out event types</small>
                </div>
              )}

            </div>

            <div className="form-group">
              <label>Extra Tag (optional)</label>
              <div className="extra-tags-selection">
                {!filterOptions ? (
                  <p>Loading tags...</p>
                ) : filterOptions.extraTags?.length > 0 ? (
                  filterOptions.extraTags.map(tag => (
                    <label 
                      key={tag} 
                      className={`extra-tag-checkbox ${formData.extraTags.includes(tag) ? 'selected' : ''}`}
                    >
                      <input
                        type="radio"
                        name="extraTag"
                        checked={formData.extraTags.includes(tag)}
                        onChange={() => handleTagToggle(tag)}
                      />
                      {extraTagLabels[tag] || tag}
                    </label>
                  ))
                ) : (
                  <p>No extra tags available</p>
                )}
              </div>
              <small>Choose at most one context tag</small>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={loading || charError}>
                {loading ? 'Submitting...' : 'Submit for Review'}
              </button>
            </div>
          </form>
        </div>
      )}

      {activeTab === 'my-submissions' && (
        <div className="submissions-section">
          {submissions && (
            <div className="submissions-stats">
              <div className="stat-card">
                <h3>{submissions.total}</h3>
                <p>Total Submissions</p>
              </div>
              <div className="stat-card">
                <h3>{submissions.pending}</h3>
                <p>Pending Review</p>
              </div>
              <div className="stat-card">
                <h3>{submissions.approved}</h3>
                <p>Approved</p>
              </div>
              <div className="stat-card">
                <h3>{submissions.rejected}</h3>
                <p>Rejected</p>
              </div>
            </div>
          )}

          <div className="submissions-filters">
            <input
              type="text"
              placeholder="Search commentary..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="all">All Status</option>
              <option value="pending">Pending</option>
              <option value="approved">Approved</option>
              <option value="rejected">Rejected</option>
            </select>
          </div>

          {loading ? (
            <div className="loading">
              <div className="spinner"></div>
              <p>Loading submissions...</p>
            </div>
          ) : submissions && submissions.submissions.length > 0 ? (
            <div className="submissions-list">
              {submissions.submissions
                .filter(s => filter === 'all' || s.status === filter)
                .filter(s => s.commentaryText.toLowerCase().includes(searchTerm.toLowerCase()))
                .map(sub => (
                  <SubmissionCard
                    key={sub.id}
                    submission={sub}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                    isEditing={editingId === sub.id}
                    setEditing={setEditingId}
                  />
                ))}
            </div>
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">📝</div>
              <h3>No submissions yet</h3>
              <p>Start by submitting your first commentary!</p>
            </div>
          )}
        </div>
      )}

      {activeTab === 'import' && (
        <div className="import-section">
          <h2>Excel Import</h2>
          <p>Import up to 1000 commentaries from an Excel file</p>

          <div className="import-steps">
            <div className={`import-step ${importStep >= 1 ? 'active' : ''} ${importStep > 1 ? 'completed' : ''}`}>
              <div className="step-number">{importStep > 1 ? '✓' : '1'}</div>
              <p>Download Template</p>
            </div>
            <div className={`import-step ${importStep >= 2 ? 'active' : ''} ${importStep > 2 ? 'completed' : ''}`}>
              <div className="step-number">{importStep > 2 ? '✓' : '2'}</div>
              <p>Upload File</p>
            </div>
            <div className={`import-step ${importStep >= 3 ? 'active' : ''}`}>
              <div className="step-number">3</div>
              <p>Confirm Import</p>
            </div>
          </div>

          {importStep === 1 && (
            <div className="import-step-content">
              <button 
                className="btn btn-primary btn-lg"
                onClick={downloadTemplate}
              >
                <HiOutlineArrowDownTray size={20} />
                Download Excel Template
              </button>
              <p style={{ marginTop: '20px', color: 'var(--text-secondary)' }}>
                Fill in the template with your commentaries, then upload it below
              </p>
              
              <div 
                className="file-upload-area"
                onClick={() => document.getElementById('file-input').click()}
                style={{ marginTop: '30px' }}
              >
                <div className="upload-icon">📄</div>
                <h3>Select Excel File</h3>
                <p>Click to browse or drag and drop your .xlsx file here</p>
                <input
                  id="file-input"
                  type="file"
                  accept=".xlsx"
                  onChange={handleFileSelect}
                />
              </div>
            </div>
          )}

          {importStep === 2 && importFile && (
            <div className="import-step-content">
              <div className="alert alert-info">
                <strong>File selected:</strong> {importFile.name}
              </div>
              
              <div className="form-actions">
                <button 
                  className="btn btn-secondary"
                  onClick={() => {
                    setImportFile(null);
                    setImportStep(1);
                  }}
                >
                  Cancel
                </button>
                <button 
                  className="btn btn-primary"
                  onClick={handleValidate}
                  disabled={loading}
                >
                  {loading ? 'Validating...' : 'Validate File'}
                </button>
              </div>
            </div>
          )}

          {importStep === 3 && validationResults && (
            <div className="validation-results">
              <h3>Validation Results</h3>
              
              <div className="validation-summary">
                <div className="validation-stat valid">
                  <h4>{validationResults.valid}</h4>
                  <p>Valid</p>
                </div>
                <div className="validation-stat warning">
                  <h4>{validationResults.warnings}</h4>
                  <p>Warnings</p>
                </div>
                <div className="validation-stat error">
                  <h4>{validationResults.errors}</h4>
                  <p>Errors</p>
                </div>
                <div className="validation-stat">
                  <h4>{validationResults.total}</h4>
                  <p>Total Rows</p>
                </div>
              </div>

              {validationResults.errorRows.length > 0 && (
                <div>
                  <h4 style={{ color: 'var(--error-color)' }}>Errors (will not be imported)</h4>
                  <div className="validation-rows">
                    {validationResults.errorRows.slice(0, 10).map((row, idx) => (
                      <div key={idx} className="validation-row error">
                        <strong>Row {row.rowNumber}:</strong> {row.commentaryText?.substring(0, 100)}...
                        <ul>
                          {row.errors.map((err, i) => <li key={i}>{err}</li>)}
                        </ul>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {validationResults.warningRows.length > 0 && (
                <div style={{ marginTop: '20px' }}>
                  <h4 style={{ color: 'var(--warning-color)' }}>Warnings (will be imported)</h4>
                  <div className="validation-rows">
                    {validationResults.warningRows.slice(0, 5).map((row, idx) => (
                      <div key={idx} className="validation-row warning">
                        <strong>Row {row.rowNumber}:</strong> {row.commentaryText?.substring(0, 100)}...
                        <ul>
                          {row.warnings.map((warn, i) => <li key={i}>{warn}</li>)}
                        </ul>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="form-actions" style={{ marginTop: '30px' }}>
                <button 
                  className="btn btn-secondary"
                  onClick={() => {
                    setImportFile(null);
                    setValidationResults(null);
                    setImportStep(1);
                  }}
                >
                  Start Over
                </button>
                <button 
                  className="btn btn-success"
                  onClick={handleConfirmImport}
                  disabled={loading || (validationResults.valid + validationResults.warnings === 0)}
                >
                  {loading ? 'Importing...' : `Import ${validationResults.valid + validationResults.warnings} Commentaries`}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function SubmissionCard({ submission, onEdit, onDelete, isEditing, setEditing }) {
  const [editText, setEditText] = useState(submission.commentaryText);

  return (
    <div className="submission-card">
      <div className="submission-header">
        <span className={`submission-status ${submission.status}`}>
          {submission.status === 'pending' && <HiOutlineClock size={16} />}
          {submission.status === 'approved' && <HiOutlineCheckCircle size={16} />}
          {submission.status === 'rejected' && <HiOutlineXCircle size={16} />}
          {submission.status}
        </span>
        <div className="submission-meta-item">
          <strong>Used:</strong> {submission.timesUsed} times
        </div>
      </div>

      {isEditing ? (
        <div className="submission-edit">
          <textarea
            value={editText}
            onChange={(e) => setEditText(e.target.value)}
            className="submission-text"
            rows="3"
          />
          <div className="form-actions">
            <button 
              className="btn btn-sm btn-secondary"
              onClick={() => {
                setEditing(null);
                setEditText(submission.commentaryText);
              }}
            >
              Cancel
            </button>
            <button 
              className="btn btn-sm btn-primary"
              onClick={() => onEdit(submission.id, editText)}
            >
              Save
            </button>
          </div>
        </div>
      ) : (
        <div className="submission-text">
          {submission.commentaryText}
        </div>
      )}

      <div className="submission-meta">
        <div className="submission-meta-item">
          <strong>Format:</strong> {submission.matchFormat}
        </div>
        <div className="submission-meta-item">
          <strong>Phase:</strong> {submission.phase}
        </div>
        <div className="submission-meta-item">
          <strong>Bowler:</strong> {submission.bowlerType}
        </div>
        <div className="submission-meta-item">
          <strong>Event:</strong> {submission.eventType}
        </div>
      </div>

      <div className="submission-meta">
        <div className="submission-meta-item">
          <strong>Created:</strong> {new Date(submission.createdAt).toLocaleDateString()}
        </div>
        {submission.reviewedAt && (
          <div className="submission-meta-item">
            <strong>Reviewed:</strong> {new Date(submission.reviewedAt).toLocaleDateString()}
          </div>
        )}
      </div>

      {submission.adminNotes && (
        <div className="admin-notes">
          <strong>Admin Notes:</strong>
          <p>{submission.adminNotes}</p>
        </div>
      )}

      {submission.status === 'pending' && (
        <div className="submission-actions">
          <button 
            className="btn btn-sm btn-secondary"
            onClick={() => setEditing(submission.id)}
          >
            <HiOutlinePencilSquare size={16} />
            Edit
          </button>
          <button 
            className="btn btn-sm btn-danger"
            onClick={() => onDelete(submission.id)}
          >
            <HiOutlineTrash size={16} />
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
