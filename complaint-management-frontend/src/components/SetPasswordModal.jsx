import { useState } from 'react';

export default function SetPasswordModal({ user, onConfirm, onCancel }) {
  const [adminPassword, setAdminPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!adminPassword) {
      setError('Enter your own current password to confirm this change.');
      return;
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters long.');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm(password, adminPassword);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update password.');
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h3 className="modal__title">Set New Password</h3>
        <p className="modal__message">
          Set a new password for <strong>{user.name}</strong> ({user.email}). They won't need their old password —
          but confirm it's really you making this change.
        </p>
        {error && <div className="alert alert--error">{error}</div>}
        <div className="form-group">
          <label>Your Admin Password (to confirm it's you)</label>
          <input
            type="password"
            className="form-control"
            value={adminPassword}
            onChange={(e) => setAdminPassword(e.target.value)}
            placeholder="Your own login password — not the user's"
            autoFocus
            required
          />
        </div>
        <div className="form-group">
          <label>New Password to Set for {user.name}</label>
          <input
            type="password"
            className="form-control"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={6}
            required
          />
        </div>
        <div className="form-group">
          <label>Confirm New Password</label>
          <input
            type="password"
            className="form-control"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            minLength={6}
            required
          />
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>Cancel</button>
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Update Password'}
          </button>
        </div>
      </form>
    </div>
  );
}
