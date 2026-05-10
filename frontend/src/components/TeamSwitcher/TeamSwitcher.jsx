import { useState, useEffect, useRef } from 'react';
import { getMyTeams, switchTeam } from '../../api/auth';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { HiOutlineChevronDown, HiOutlinePlus, HiOutlineCheck } from 'react-icons/hi2';
import './TeamSwitcher.css';

export default function TeamSwitcher() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [teams, setTeams] = useState([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [canCreateSecondary, setCanCreateSecondary] = useState(false);
  const dropdownRef = useRef(null);

  useEffect(() => {
    if (user?.hasMultipleTeams || user?.isSupporter) {
      loadTeams();
    }
  }, [user]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowDropdown(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const loadTeams = async () => {
    try {
      const res = await getMyTeams();
      setTeams(res.data.teams || []);
      setCanCreateSecondary(res.data.canCreateSecondary || false);
    } catch (error) {
      console.error('Failed to load teams:', error);
    }
  };

  const handleSwitchTeam = async (teamId) => {
    if (teamId === user?.activeTeamId) {
      setShowDropdown(false);
      return;
    }

    setSwitching(true);
    try {
      const res = await switchTeam(teamId);
      toast.success(res.data.message);
      await refreshUser();
      setShowDropdown(false);
      // Refresh the page to update all data
      window.location.reload();
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to switch team');
    } finally {
      setSwitching(false);
    }
  };

  const handleCreateSecondary = () => {
    setShowDropdown(false);
    navigate('/team-setup-secondary');
  };

  // Don't show if user has no teams or not a supporter
  if (!user?.hasMultipleTeams && !user?.isSupporter) {
    return null;
  }

  // Find active team
  const activeTeam = teams.find(t => t.isActive) || teams[0];

  return (
    <div className="team-switcher" ref={dropdownRef}>
      <button
        className="team-switcher-trigger"
        onClick={() => setShowDropdown(!showDropdown)}
        disabled={switching}
      >
        <div className="team-switcher-info">
          {activeTeam?.teamProfilePicUrl ? (
            <img
              src={`/api/files/${activeTeam.teamProfilePicUrl}`}
              alt={activeTeam.teamName}
              className="team-switcher-logo"
            />
          ) : (
            <div className="team-switcher-logo-placeholder">
              {activeTeam?.teamName?.slice(0, 2).toUpperCase() || 'T'}
            </div>
          )}
          <div className="team-switcher-text">
            <span className="team-switcher-label">Active Team</span>
            <span className="team-switcher-name">{activeTeam?.teamName || 'Select Team'}</span>
          </div>
        </div>
        <HiOutlineChevronDown className={`team-switcher-icon ${showDropdown ? 'rotated' : ''}`} />
      </button>

      {showDropdown && (
        <div className="team-switcher-dropdown">
          <div className="team-switcher-header">Your Teams</div>
          {teams.map((team) => (
            <button
              key={team.id}
              className={`team-switcher-item ${team.isActive ? 'active' : ''}`}
              onClick={() => handleSwitchTeam(team.id)}
              disabled={switching}
            >
              {team.teamProfilePicUrl ? (
                <img
                  src={`/api/files/${team.teamProfilePicUrl}`}
                  alt={team.teamName}
                  className="team-switcher-item-logo"
                />
              ) : (
                <div className="team-switcher-item-logo-placeholder">
                  {team.teamName.slice(0, 2).toUpperCase()}
                </div>
              )}
              <div className="team-switcher-item-info">
                <span className="team-switcher-item-name">{team.teamName}</span>
                <span className="team-switcher-item-country">{team.country}</span>
              </div>
              {team.isActive && <HiOutlineCheck className="team-switcher-check" />}
            </button>
          ))}

          {canCreateSecondary && (
            <>
              <div className="team-switcher-divider" />
              <button
                className="team-switcher-create"
                onClick={handleCreateSecondary}
                disabled={switching}
              >
                <HiOutlinePlus className="team-switcher-plus" />
                Create Secondary Team
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
