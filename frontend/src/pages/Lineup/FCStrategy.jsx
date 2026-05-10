import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getFCState, saveFCStrategy } from '../../api/auth';
import toast from 'react-hot-toast';
import './FCStrategy.css';

export default function FCStrategy() {
  const { fixtureId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [state, setState] = useState(null);

  const [declareInn1, setDeclareInn1] = useState('');
  const [declareInn2Lead, setDeclareInn2Lead] = useState('');
  const [followOn, setFollowOn] = useState(true);
  const [declareInn3Lead, setDeclareInn3Lead] = useState('');
  const canSetInn1 = state?.canSetInn1 ?? true;
  const canSetInn2 = state?.canSetInn2 ?? true;
  const canSetFollowOn = state?.canSetFollowOn ?? true;
  const canSetInn3 = state?.canSetInn3 ?? true;
  const inn3DependsOnFollowOn = state?.inn3DependsOnFollowOn ?? false;

  useEffect(() => {
    getFCState(fixtureId)
      .then((res) => {
        const d = res.data;
        setState(d);
        if (d.declareInn1 != null) setDeclareInn1(String(d.declareInn1));
        if (d.declareInn2Lead != null) setDeclareInn2Lead(String(d.declareInn2Lead));
        if (d.followOn != null) setFollowOn(d.followOn);
        if (d.declareInn3Lead != null) setDeclareInn3Lead(String(d.declareInn3Lead));
      })
      .catch(() => toast.error('Failed to load FC state'))
      .finally(() => setLoading(false));
  }, [fixtureId]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = {};
      if (canSetInn1) payload.declareInn1 = declareInn1 ? parseInt(declareInn1, 10) : null;
      if (canSetInn2) payload.declareInn2Lead = declareInn2Lead ? parseInt(declareInn2Lead, 10) : null;
      if (canSetFollowOn) payload.followOn = followOn;
      if (canSetInn3) payload.declareInn3Lead = declareInn3Lead ? parseInt(declareInn3Lead, 10) : null;
      await saveFCStrategy(fixtureId, payload);
      toast.success('Day 2 strategy updated!');
      navigate(`/match/${fixtureId}/preview`);
    } catch (e) {
      toast.error(e.response?.data?.message || 'Failed to save strategy');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="fcs-page">
        <div className="fcs-loading">Loading match state...</div>
      </div>
    );
  }

  if (!state) {
    return (
      <div className="fcs-page">
        <div className="fcs-loading">Match not found.</div>
      </div>
    );
  }

  return (
    <div className="fcs-page">
      <header className="fcs-header">
        <h1 className="fcs-title">Update Day 2 Strategy</h1>
        <p className="fcs-sub">Review Day 1 scorecard and adjust your strategy for Day 2.</p>
      </header>

      {/* Day 1 Innings Summary */}
      {state.innings && state.innings.length > 0 && (
        <div className="fcs-section">
          <h2 className="fcs-section-title">Day 1 Summary</h2>
          <div className="fcs-innings-list">
            {state.innings.map((inn, idx) => (
              <div key={idx} className="fcs-innings-card">
                <div className="fcs-inn-num">Innings {inn.inningsNumber}</div>
                <div className="fcs-inn-team">{inn.battingTeamName}</div>
                <div className="fcs-inn-score">
                  {inn.totalRuns}/{inn.totalWickets}
                  <span className="fcs-inn-overs">({inn.totalOvers} ov)</span>
                </div>
                <div className="fcs-inn-tags">
                  {inn.allOut && <span className="fcs-tag fcs-tag-red">All Out</span>}
                  {inn.declared && <span className="fcs-tag fcs-tag-blue">Declared</span>}
                  {inn.interrupted && <span className="fcs-tag fcs-tag-amber">Day End</span>}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Strategy Form */}
      <div className="fcs-section">
        <h2 className="fcs-section-title">Day 2 Strategy</h2>
        <div className="fcs-form">
          <div className="fcs-row">
            <label className="fcs-label">1st Innings Declaration</label>
            <div className="fcs-input-wrap">
              <input
                type="number"
                min="0"
                placeholder="Score total (e.g. 350)"
                value={declareInn1}
                onChange={(e) => setDeclareInn1(e.target.value)}
                disabled={!canSetInn1}
                className="fcs-input"
              />
              <span className="fcs-hint">
                {canSetInn1
                  ? 'Declare when your team reaches this total. Leave empty for no declaration.'
                  : 'Not applicable: your team did not bat in innings 1.'}
              </span>
            </div>
          </div>
          <div className="fcs-row">
            <label className="fcs-label">2nd Innings Declaration (Lead)</label>
            <div className="fcs-input-wrap">
              <input
                type="number"
                min="0"
                placeholder="Lead runs (e.g. 150)"
                value={declareInn2Lead}
                onChange={(e) => setDeclareInn2Lead(e.target.value)}
                disabled={!canSetInn2}
                className="fcs-input"
              />
              <span className="fcs-hint">
                {canSetInn2
                  ? 'Declare when your team leads by this many runs.'
                  : 'Not applicable: your team did not bat in innings 2.'}
              </span>
            </div>
          </div>
          <div className="fcs-row">
            <label className="fcs-label">Follow-on (if leading by 200+)</label>
            <div className="fcs-input-wrap">
              <div className="fcs-toggle">
                <button
                  className={`fcs-toggle-btn ${followOn ? 'active' : ''}`}
                  onClick={() => setFollowOn(true)}
                  disabled={!canSetFollowOn}
                >
                  Yes — Enforce
                </button>
                <button
                  className={`fcs-toggle-btn ${!followOn ? 'active' : ''}`}
                  onClick={() => setFollowOn(false)}
                  disabled={!canSetFollowOn}
                >
                  No — Bat Again
                </button>
              </div>
              <span className="fcs-hint">{canSetFollowOn ? 'If your team leads by 200+, enforce follow-on or bat again.' : 'Not applicable: follow-on decision belongs to innings-1 batting side.'}</span>
            </div>
          </div>
          <div className="fcs-row">
            <label className="fcs-label">3rd Innings Declaration (Lead)</label>
            <div className="fcs-input-wrap">
              <input
                type="number"
                min="0"
                placeholder="Lead runs (e.g. 250)"
                value={declareInn3Lead}
                onChange={(e) => setDeclareInn3Lead(e.target.value)}
                disabled={!canSetInn3}
                className="fcs-input"
              />
              <span className="fcs-hint">
                {!canSetInn3
                  ? 'Not applicable: your team is not batting in innings 3.'
                  : inn3DependsOnFollowOn
                    ? 'Applies only if your team bats in innings 3 (depends on follow-on choice).'
                    : 'Declare when your team\'s overall lead reaches this.'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Actions */}
      <div className="fcs-actions">
        <button className="fcs-btn fcs-btn-secondary" onClick={() => navigate(-1)}>
          Cancel
        </button>
        <button className="fcs-btn fcs-btn-primary" onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Strategy'}
        </button>
      </div>
    </div>
  );
}
