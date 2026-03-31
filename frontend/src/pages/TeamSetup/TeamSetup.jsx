import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { setupTeam, checkCountryAvailability } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import toast from 'react-hot-toast';
import { COUNTRIES, COUNTRY_MATCH_TIMES } from '../../constants/countries';
import './TeamSetup.css';

export default function TeamSetup() {
  const navigate = useNavigate();
  const { updateUser, user } = useAuth();
  const [form, setForm] = useState({ teamName: '', country: '' });
  const [loading, setLoading] = useState(false);
  const [availability, setAvailability] = useState(null); // { available, matchStartTimeUtc }
  const [checking, setChecking] = useState(false);

  const checkAvailability = useCallback(async (country) => {
    if (!country) { setAvailability(null); return; }
    setChecking(true);
    try {
      const { data } = await checkCountryAvailability(country);
      setAvailability(data);
    } catch {
      setAvailability(null);
    } finally {
      setChecking(false);
    }
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (name === 'country') {
      checkAvailability(value);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (availability && !availability.available) {
      toast.error('Leagues are full for this country. Please choose a different country.');
      return;
    }
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

  const matchTime = form.country ? COUNTRY_MATCH_TIMES[form.country] : null;
  const countryFull = availability && !availability.available;

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

            {form.country && (
              <div className="ts-country-info">
                {checking ? (
                  <span className="ts-checking">Checking availability…</span>
                ) : countryFull ? (
                  <span className="ts-full">
                    Leagues are full for {form.country}. Please choose a different country.
                  </span>
                ) : availability?.available ? (
                  <span className="ts-available">Slots available</span>
                ) : null}
                {matchTime && (
                  <span className="ts-match-time">
                    League matches start daily at <strong>{matchTime} UTC</strong>
                  </span>
                )}
              </div>
            )}
          </div>

          <button type="submit" className="auth-btn" disabled={loading || countryFull}>
            {loading ? 'Creating Team...' : 'Create Team & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
}
