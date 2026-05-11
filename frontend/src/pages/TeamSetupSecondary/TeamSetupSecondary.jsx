import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { createSecondaryTeam, getMyTeams } from '../../api/auth';
import { COUNTRIES } from '../../constants/countries';
import toast from 'react-hot-toast';
import { HiOutlineArrowLeft, HiOutlineInformationCircle } from 'react-icons/hi2';
import './TeamSetupSecondary.css';

export default function TeamSetupSecondary() {
  const navigate = useNavigate();
  const { user, refreshUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const [existingTeams, setExistingTeams] = useState([]);
  const [formData, setFormData] = useState({
    teamName: '',
    country: '',
    groundName: '',
  });
  const [errors, setErrors] = useState({});

  useEffect(() => {
    // Check if user is a supporter
    if (!user?.isSupporter) {
      toast.error('Only supporters can create a secondary team');
      navigate('/membership');
      return;
    }

    // Load existing teams to filter out their countries
    loadExistingTeams();
  }, [user, navigate]);

  const loadExistingTeams = async () => {
    try {
      const res = await getMyTeams();
      setExistingTeams(res.data.teams || []);
      
      // If user already has 2 teams, redirect
      if (res.data.teams?.length >= 2) {
        toast.error('You already have the maximum number of teams');
        navigate('/');
      }
    } catch (error) {
      console.error('Failed to load teams:', error);
    }
  };

  // Filter out countries already used by user's teams
  const availableCountries = COUNTRIES.filter(
    (country) => !existingTeams.some((team) => team.country === country)
  );

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    // Clear error for this field
    if (errors[name]) {
      setErrors((prev) => ({ ...prev, [name]: null }));
    }
  };

  const validate = () => {
    const newErrors = {};

    if (!formData.teamName.trim()) {
      newErrors.teamName = 'Team name is required';
    } else if (formData.teamName.length < 3) {
      newErrors.teamName = 'Team name must be at least 3 characters';
    } else if (formData.teamName.length > 50) {
      newErrors.teamName = 'Team name must be less than 50 characters';
    }

    if (!formData.country) {
      newErrors.country = 'Country is required';
    }

    if (!formData.groundName.trim()) {
      newErrors.groundName = 'Ground name is required';
    } else if (formData.groundName.length < 3) {
      newErrors.groundName = 'Ground name must be at least 3 characters';
    } else if (formData.groundName.length > 50) {
      newErrors.groundName = 'Ground name must be less than 50 characters';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validate()) {
      toast.error('Please fix the errors');
      return;
    }

    setLoading(true);
    try {
      const res = await createSecondaryTeam(formData);
      toast.success(res.data.message || 'Secondary team created successfully!');
      await refreshUser();
      navigate('/team-setup'); // Redirect to team setup to complete the setup
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to create secondary team');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="team-setup-secondary-page">
      <div className="team-setup-secondary-container">
        <button className="tss-back-btn" onClick={() => navigate(-1)}>
          <HiOutlineArrowLeft />
          Back
        </button>

        <div className="tss-header">
          <h1 className="tss-title">Create Secondary Team</h1>
          <p className="tss-subtitle">
            As a supporter, you can manage a second team in a different country
          </p>
        </div>

        <div className="tss-info-box">
          <HiOutlineInformationCircle className="tss-info-icon" />
          <div className="tss-info-content">
            <h3>Important Notes:</h3>
            <ul>
              <li>Your secondary team must be from a different country</li>
              <li>You cannot transfer players or bid between your teams</li>
              <li>Each team operates completely independently</li>
              <li>You can switch between teams anytime from the sidebar</li>
            </ul>
          </div>
        </div>

        {existingTeams.length > 0 && (
          <div className="tss-existing-teams">
            <h3>Your Existing Teams:</h3>
            <div className="tss-team-list">
              {existingTeams.map((team) => (
                <div key={team.id} className="tss-team-card">
                  {team.teamProfilePicUrl ? (
                    <img
                      src={`/api/files/${team.teamProfilePicUrl}`}
                      alt={team.teamName}
                      className="tss-team-logo"
                    />
                  ) : (
                    <div className="tss-team-logo-placeholder">
                      {team.teamName.slice(0, 2).toUpperCase()}
                    </div>
                  )}
                  <div className="tss-team-info">
                    <span className="tss-team-name">{team.teamName}</span>
                    <span className="tss-team-country">{team.country}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <form className="tss-form" onSubmit={handleSubmit}>
          <div className="tss-form-group">
            <label htmlFor="teamName" className="tss-label">
              Team Name <span className="required">*</span>
            </label>
            <input
              type="text"
              id="teamName"
              name="teamName"
              value={formData.teamName}
              onChange={handleChange}
              className={`tss-input ${errors.teamName ? 'error' : ''}`}
              placeholder="Enter your team name"
              maxLength={50}
              disabled={loading}
            />
            {errors.teamName && <span className="tss-error">{errors.teamName}</span>}
          </div>

          <div className="tss-form-group">
            <label htmlFor="country" className="tss-label">
              Country <span className="required">*</span>
            </label>
            <select
              id="country"
              name="country"
              value={formData.country}
              onChange={handleChange}
              className={`tss-select ${errors.country ? 'error' : ''}`}
              disabled={loading}
            >
              <option value="">Select a country</option>
              {availableCountries.map((country) => (
                <option key={country} value={country}>
                  {country}
                </option>
              ))}
            </select>
            {errors.country && <span className="tss-error">{errors.country}</span>}
            {availableCountries.length === 0 && (
              <span className="tss-info-text">
                No available countries (your primary team uses all eligible countries)
              </span>
            )}
          </div>

          <div className="tss-form-group">
            <label htmlFor="groundName" className="tss-label">
              Ground Name <span className="required">*</span>
            </label>
            <input
              type="text"
              id="groundName"
              name="groundName"
              value={formData.groundName}
              onChange={handleChange}
              className={`tss-input ${errors.groundName ? 'error' : ''}`}
              placeholder="Enter your home ground name"
              maxLength={50}
              disabled={loading}
            />
            {errors.groundName && <span className="tss-error">{errors.groundName}</span>}
          </div>

          <button
            type="submit"
            className="tss-submit-btn"
            disabled={loading || availableCountries.length === 0}
          >
            {loading ? 'Creating Team...' : 'Create Secondary Team'}
          </button>
        </form>
      </div>
    </div>
  );
}
