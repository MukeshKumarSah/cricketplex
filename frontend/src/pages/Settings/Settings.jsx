import { useState, useEffect, useRef } from 'react';
import { useAuth } from '../../context/AuthContext';
import {
  getSettings,
  uploadProfilePic,
  uploadTeamPic,
  updateTeam,
  changePassword,
} from '../../api/auth';
import toast from 'react-hot-toast';
import { HiOutlineCamera } from 'react-icons/hi2';
import './Settings.css';

export default function Settings() {
  const { user, updateUser } = useAuth();
  const profileInputRef = useRef(null);
  const teamPicInputRef = useRef(null);

  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(true);

  // Team form
  const [teamForm, setTeamForm] = useState({ teamName: '', groundName: '' });
  const [teamSaving, setTeamSaving] = useState(false);

  // Password form
  const [pwForm, setPwForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmNewPassword: '',
  });
  const [pwSaving, setPwSaving] = useState(false);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const res = await getSettings();
      setSettings(res.data);
      setTeamForm({
        teamName: res.data.team?.teamName || '',
        groundName: res.data.team?.groundName || '',
      });
    } catch {
      toast.error('Failed to load settings');
    } finally {
      setLoading(false);
    }
  };

  const handleProfilePicUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const res = await uploadProfilePic(file);
      const newUrl = res.data.profilePicUrl;
      setSettings((prev) => ({
        ...prev,
        user: { ...prev.user, profilePicUrl: newUrl },
      }));
      updateUser({ ...user, profilePicUrl: newUrl });
      toast.success('Profile picture updated');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed');
    }
  };

  const handleTeamPicUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const res = await uploadTeamPic(file);
      const newUrl = res.data.teamProfilePicUrl;
      setSettings((prev) => ({
        ...prev,
        team: { ...prev.team, teamProfilePicUrl: newUrl },
      }));
      toast.success('Team picture updated');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed');
    }
  };

  const handleTeamSave = async (e) => {
    e.preventDefault();
    setTeamSaving(true);
    try {
      await updateTeam(teamForm);
      toast.success('Team details updated');
      loadSettings();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Update failed');
    } finally {
      setTeamSaving(false);
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    if (pwForm.newPassword !== pwForm.confirmNewPassword) {
      toast.error('New passwords do not match');
      return;
    }
    setPwSaving(true);
    try {
      await changePassword(pwForm);
      toast.success('Password changed successfully');
      setPwForm({ currentPassword: '', newPassword: '', confirmNewPassword: '' });
    } catch (err) {
      toast.error(err.response?.data?.message || 'Password change failed');
    } finally {
      setPwSaving(false);
    }
  };

  const profileInitials = user?.name
    ? user.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)
    : '?';

  const teamInitials = settings?.team?.teamName
    ? settings.team.teamName.slice(0, 2).toUpperCase()
    : 'T';

  if (loading) {
    return <div className="settings-loading">Loading settings...</div>;
  }

  return (
    <div className="settings-page">
      <h1 className="settings-title">Settings</h1>

      <div className="settings-grid">
        {/* Profile Picture */}
        <section className="settings-card">
          <h2>Profile Picture</h2>
          <div className="avatar-section">
            <div
              className="avatar-wrapper"
              onClick={() => profileInputRef.current?.click()}
            >
              {settings?.user?.profilePicUrl ? (
                <img
                  src={settings.user.profilePicUrl}
                  alt="Profile"
                  className="avatar-img"
                />
              ) : (
                <span className="avatar-initials">{profileInitials}</span>
              )}
              <div className="avatar-overlay">
                <HiOutlineCamera />
              </div>
            </div>
            <div className="avatar-info">
              <p className="avatar-name">{settings?.user?.name}</p>
              <p className="avatar-sub">@{settings?.user?.username}</p>
              <button
                className="upload-btn"
                onClick={() => profileInputRef.current?.click()}
              >
                Change Photo
              </button>
            </div>
            <input
              ref={profileInputRef}
              type="file"
              accept="image/*"
              hidden
              onChange={handleProfilePicUpload}
            />
          </div>
        </section>

        {/* Team Picture */}
        <section className="settings-card">
          <h2>Team Picture</h2>
          <div className="avatar-section">
            <div
              className="avatar-wrapper team-avatar"
              onClick={() => teamPicInputRef.current?.click()}
            >
              {settings?.team?.teamProfilePicUrl ? (
                <img
                  src={settings.team.teamProfilePicUrl}
                  alt="Team"
                  className="avatar-img"
                />
              ) : (
                <span className="avatar-initials team">{teamInitials}</span>
              )}
              <div className="avatar-overlay">
                <HiOutlineCamera />
              </div>
            </div>
            <div className="avatar-info">
              <p className="avatar-name">{settings?.team?.teamName}</p>
              <p className="avatar-sub">{settings?.team?.country}</p>
              <button
                className="upload-btn"
                onClick={() => teamPicInputRef.current?.click()}
              >
                Change Team Photo
              </button>
            </div>
            <input
              ref={teamPicInputRef}
              type="file"
              accept="image/*"
              hidden
              onChange={handleTeamPicUpload}
            />
          </div>
        </section>

        {/* Team Details */}
        <section className="settings-card wide">
          <h2>Team Details</h2>
          <form onSubmit={handleTeamSave} className="settings-form">
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="teamName">Team Name</label>
                <input
                  id="teamName"
                  type="text"
                  value={teamForm.teamName}
                  onChange={(e) =>
                    setTeamForm((p) => ({ ...p, teamName: e.target.value }))
                  }
                  placeholder="Enter team name"
                />
              </div>
              <div className="form-group">
                <label htmlFor="groundName">Ground Name</label>
                <input
                  id="groundName"
                  type="text"
                  value={teamForm.groundName}
                  onChange={(e) =>
                    setTeamForm((p) => ({ ...p, groundName: e.target.value }))
                  }
                  placeholder="Enter ground name"
                />
              </div>
            </div>
            <button type="submit" className="save-btn" disabled={teamSaving}>
              {teamSaving ? 'Saving...' : 'Save Changes'}
            </button>
          </form>
        </section>

        {/* Change Password */}
        <section className="settings-card wide">
          <h2>Change Password</h2>
          <form onSubmit={handlePasswordChange} className="settings-form">
            <div className="form-group">
              <label htmlFor="currentPassword">Current Password</label>
              <input
                id="currentPassword"
                type="password"
                value={pwForm.currentPassword}
                onChange={(e) =>
                  setPwForm((p) => ({ ...p, currentPassword: e.target.value }))
                }
                placeholder="Enter current password"
                required
              />
            </div>
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="newPassword">New Password</label>
                <input
                  id="newPassword"
                  type="password"
                  value={pwForm.newPassword}
                  onChange={(e) =>
                    setPwForm((p) => ({ ...p, newPassword: e.target.value }))
                  }
                  placeholder="Min 8 characters"
                  required
                  minLength={8}
                />
              </div>
              <div className="form-group">
                <label htmlFor="confirmNewPassword">Confirm New Password</label>
                <input
                  id="confirmNewPassword"
                  type="password"
                  value={pwForm.confirmNewPassword}
                  onChange={(e) =>
                    setPwForm((p) => ({
                      ...p,
                      confirmNewPassword: e.target.value,
                    }))
                  }
                  placeholder="Re-enter new password"
                  required
                />
              </div>
            </div>
            <button type="submit" className="save-btn" disabled={pwSaving}>
              {pwSaving ? 'Changing...' : 'Change Password'}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
