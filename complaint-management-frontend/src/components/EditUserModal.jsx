import { useState } from 'react';
import { phoneError } from '../utils/phone';

/**
 * Admin edit dialog for a user (opened from the Actions menu on the Users page).
 *
 * Name and phone can be changed for anyone; officials can also be moved to a
 * different department. Email and role are shown read-only: the email is the
 * login identity, so changing it here would silently lock the person out.
 *
 * props:
 *  - user: the row being edited
 *  - departments: [{ id, name, active }]
 *  - submitting / error: controlled by the parent, which owns the API call
 *  - onSave: ({ name, phone, departmentId }) => void
 *  - onCancel: () => void
 */
export default function EditUserModal({ user, departments, submitting, error, onSave, onCancel }) {
  const [name, setName] = useState(user.name || '');
  const [phone, setPhone] = useState(user.phone || '');
  const [departmentId, setDepartmentId] = useState(user.departmentId ?? '');
  const [localError, setLocalError] = useState('');

  const isOfficial = user.role === 'OFFICIAL';
  const departmentChanged = isOfficial && departmentId !== '' && Number(departmentId) !== user.departmentId;
  // Only offer active departments, plus the official's current one so the dropdown reflects reality.
  const choices = departments.filter((d) => d.active || d.id === user.departmentId);

  const handleSubmit = (e) => {
    e.preventDefault();
    setLocalError('');
    if (!name.trim()) {
      setLocalError('Name is required.');
      return;
    }
    const phoneProblem = phoneError(phone);
    if (phoneProblem) {
      setLocalError(phoneProblem);
      return;
    }
    onSave({
      name: name.trim(),
      phone: phone.trim(),
      departmentId: isOfficial && departmentId !== '' ? Number(departmentId) : null,
    });
  };

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h3 className="modal__title">Edit User</h3>
        <p className="modal__message">
          Update details for <strong>{user.name}</strong>. Email and role can't be changed here.
        </p>
        {(localError || error) && <div className="alert alert--error">{localError || error}</div>}

        <div className="form-group">
          <label>Name</label>
          <input className="form-control" value={name} onChange={(e) => setName(e.target.value)} autoFocus required />
        </div>
        <div className="form-group">
          <label>Email</label>
          <input className="form-control" value={user.email} disabled />
        </div>
        <div className="form-group">
          <label>Mobile Number</label>
          <input type="tel" inputMode="tel" className="form-control" required maxLength={17}
            placeholder="10-digit mobile number" value={phone} onChange={(e) => setPhone(e.target.value)} />
        </div>
        <div className="form-group">
          <label>Role</label>
          <input className="form-control" value={user.role} disabled />
        </div>
        {isOfficial && (
          <div className="form-group">
            <label>Department</label>
            <select className="form-control" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              {user.departmentId == null && <option value="">No department</option>}
              {choices.map((d) => <option key={d.id} value={d.id}>{d.name}{d.active ? '' : ' (inactive)'}</option>)}
            </select>
            {departmentChanged && (
              <p style={{ fontSize: '0.78rem', color: 'var(--c-text-faint)', marginTop: 6 }}>
                If {user.name} still has active complaints assigned, you'll be asked who should take them over.
              </p>
            )}
          </div>
        )}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>Cancel</button>
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
