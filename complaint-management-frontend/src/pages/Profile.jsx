import { useState, useEffect } from 'react';
import { profileAPI } from '../services/api';

export default function Profile() {
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ name: '', phone: '' });
  const [pwForm, setPwForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [pwMessage, setPwMessage] = useState('');
  const [pwError, setPwError] = useState('');

  const load = () => {
    profileAPI.get().then((res) => {
      setProfile(res.data);
      setForm({ name: res.data.name, phone: res.data.phone || '' });
    });
  };

  useEffect(() => { load(); }, []);

  const handleUpdate = async (e) => {
    e.preventDefault();
    setMessage(''); setError('');
    try {
      await profileAPI.update(form);
      setMessage('Profile updated successfully.');
      load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update profile');
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    setPwMessage(''); setPwError('');
    if (pwForm.newPassword !== pwForm.confirmPassword) {
      setPwError('New passwords do not match.');
      return;
    }
    try {
      await profileAPI.changePassword(pwForm);
      setPwMessage('Password changed successfully.');
      setPwForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) {
      setPwError(err.response?.data?.error || 'Failed to change password');
    }
  };

  if (!profile) return <div className="loading-block">Loading profile…</div>;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>My Profile</h1>
          <p className="subtitle">Manage your account details.</p>
        </div>
      </div>

      <div className="form-row">
        <div className="card">
          <h3 className="card__title">Account Details</h3>
          {message && <div className="alert alert--success">{message}</div>}
          {error && <div className="alert alert--error">{error}</div>}
          <form onSubmit={handleUpdate}>
            <div className="form-group">
              <label>Full Name</label>
              <input className="form-control" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Phone</label>
              <input className="form-control" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Email</label>
              <input className="form-control" value={profile.email} disabled />
              <span className="hint">Email cannot be changed.</span>
            </div>
            <div className="form-group">
              <label>Role</label>
              <input className="form-control" value={profile.role} disabled />
            </div>
            <button type="submit" className="btn btn--primary">Save Changes</button>
          </form>
        </div>

        <div className="card">
          <h3 className="card__title">Change Password</h3>
          {pwMessage && <div className="alert alert--success">{pwMessage}</div>}
          {pwError && <div className="alert alert--error">{pwError}</div>}
          <form onSubmit={handlePasswordChange}>
            <div className="form-group">
              <label>Current Password</label>
              <input type="password" className="form-control" required
                value={pwForm.currentPassword} onChange={(e) => setPwForm({ ...pwForm, currentPassword: e.target.value })} />
            </div>
            <div className="form-group">
              <label>New Password</label>
              <input type="password" className="form-control" required minLength={6}
                value={pwForm.newPassword} onChange={(e) => setPwForm({ ...pwForm, newPassword: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Confirm New Password</label>
              <input type="password" className="form-control" required minLength={6}
                value={pwForm.confirmPassword} onChange={(e) => setPwForm({ ...pwForm, confirmPassword: e.target.value })} />
            </div>
            <button type="submit" className="btn btn--primary">Change Password</button>
          </form>
        </div>
      </div>
    </div>
  );
}
