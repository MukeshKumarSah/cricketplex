import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { setupTeam } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import toast from 'react-hot-toast';
import { COUNTRIES } from '../../constants/countries';
import './TeamSetup.css';

export default function TeamSetup() {
  const navigate = useNavigate();
  const { updateUser, user } = useAuth();
  const [form, setForm] = useState({ teamName: '', country: '' });
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await setupTeam(form);
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
              name="teamName"
              type="text"
              placeholder="Enter your team name"
              value={form.teamName}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="country">Country</label>
            <select
              id="country"
              name="country"
              value={form.country}
              onChange={handleChange}
              required
            >
              <option value="">Select a country</option>
              {COUNTRIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Creating Team...' : 'Create Team & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
}
