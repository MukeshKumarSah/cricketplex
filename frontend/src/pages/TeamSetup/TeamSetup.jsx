import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { setupTeam, getAllCountryAvailability } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import toast from 'react-hot-toast';
import './TeamSetup.css';

export default function TeamSetup() {
  const navigate = useNavigate();
  const { updateUser, user } = useAuth();
  const [teamName, setTeamName] = useState('');
  const [selected, setSelected] = useState(null);
  const [countries, setCountries] = useState([]);
  const [loadingCountries, setLoadingCountries] = useState(true);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getAllCountryAvailability()
      .then(res => setCountries(res.data))
      .catch(() => toast.error('Failed to load countries'))
      .finally(() => setLoadingCountries(false));
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selected || !selected.available) {
      toast.error('Please select a country with available slots.');
      return;
    }
    if (!teamName.trim()) {
      toast.error('Please enter a team name.');
      return;
    }
    setLoading(true);
    try {
      await setupTeam({ teamName: teamName.trim(), country: selected.country });
      updateUser({ ...user, teamSetupDone: true });
      toast.success('Team created! Welcome to CricketPlex!');
      navigate('/');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Team setup failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="team-setup-container">
      <div className="team-setup-card">
        <div className="team-setup-header">
          <h1>Set Up Your Team</h1>
          <p>Choose a name and country for your cricket team</p>
        </div>

        <form onSubmit={handleSubmit} className="team-setup-form">
          <div className="form-group">
            <label htmlFor="teamName">Team Name</label>
            <input
              id="teamName"
              type="text"
              placeholder="Enter your team name"
              value={teamName}
              onChange={(e) => setTeamName(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label>Select Country</label>
            {loadingCountries ? (
              <div className="ts-loading">Loading countries…</div>
            ) : (
              <div className="ts-country-grid">
                {countries.map((c) => {
                  const isSelected = selected?.country === c.country;
                  const full = !c.available;
                  return (
                    <button
                      type="button"
                      key={c.country}
                      className={`ts-country-card${isSelected ? ' selected' : ''}${full ? ' full' : ''}`}
                      onClick={() => !full && setSelected(c)}
                      disabled={full}
                    >
                      <span className="ts-card-name">{c.country}</span>
                      <span className="ts-card-meta">
                        <span className={`ts-card-status ${full ? 'full' : 'open'}`}>
                          {full ? 'Full' : `${c.slots} slot${c.slots !== 1 ? 's' : ''}`}
                        </span>
                        <span className="ts-card-time">{c.matchStartTimeUtc} UTC</span>
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {selected && (
            <div className="ts-selected-info">
              <span>Selected: <strong>{selected.country}</strong></span>
              <span className="ts-selected-time">
                Matches at <strong>{selected.matchStartTimeUtc} UTC</strong> daily
              </span>
            </div>
          )}

          <button type="submit" className="auth-btn" disabled={loading || !selected?.available}>
            {loading ? 'Creating Team...' : 'Create Team & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
}
