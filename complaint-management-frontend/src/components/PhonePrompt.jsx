import { useEffect, useState } from 'react';
import { profileAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { phoneError } from '../utils/phone';

// Users who registered before the mobile number became mandatory have none on file.
// They can still log in; this blocking dialog asks for it once. Ids already confirmed
// to have a number are remembered so page changes don't re-check.
const confirmedUsers = new Set();

export default function PhonePrompt({ force = false, onSaved, onCancel } = {}) {
  const { user, logout } = useAuth();
  const [profile, setProfile] = useState(null); // set only when this user has no phone on file
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    // Only citizens must have a mobile number; officials and admins may add one later in their profile.
    if (!user || user.role !== 'CITIZEN' || (!force && confirmedUsers.has(user.id))) return undefined;
    let cancelled = false;
    profileAPI.get()
      .then((res) => {
        if (cancelled) return;
        if (res.data.phone && String(res.data.phone).trim() && !force) confirmedUsers.add(user.id);
        else setProfile(res.data);
      })
      .catch(() => { /* if the check fails, don't block the user */ });
    return () => { cancelled = true; };
  }, [user, force]);

  if (!profile) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    const problem = phoneError(phone);
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    setError('');
    try {
      await profileAPI.update({ name: profile.name, phone });
      confirmedUsers.add(user.id);
      setProfile(null);
      if (onSaved) onSaved(phone);
    } catch (err) {
      setError(err.response?.data?.error || 'Could not save your mobile number. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-overlay">
      <form className="modal" onSubmit={handleSubmit}>
        <h3 className="modal__title">Add your mobile number</h3>
        <p className="modal__message">
          A mobile number is now required on every account so the department can reach you about complaints.
          Please add yours to continue.
        </p>
        {error && <div className="alert alert--error">{error}</div>}
        <div className="form-group">
          <label>Mobile Number</label>
          <input type="tel" inputMode="tel" autoComplete="tel" className="form-control" autoFocus required
            maxLength={17} placeholder="10-digit mobile number"
            value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
        <div className="modal__actions">
          {force
            ? <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={saving}>Cancel</button>
            : <button type="button" className="btn btn--ghost" onClick={logout} disabled={saving}>Log out</button>}
          <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? 'Saving…' : 'Save & Continue'}</button>
        </div>
      </form>
    </div>
  );
}
