import { useState } from 'react';

/**
 * Confirmation dialog for permanently deleting a complaint. The admin has to
 * re-enter their own password, same safeguard as the admin password reset.
 *
 * props:
 *  - complaint: { complaintNumber, title }
 *  - onConfirm: (adminPassword) => Promise  (throw to show the error inside the dialog)
 *  - onCancel: () => void
 */
export default function DeleteComplaintModal({ complaint, onConfirm, onCancel }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!password) {
      setError('Enter your admin password to confirm.');
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm(password);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete the complaint.');
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={submitting ? undefined : onCancel}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h3 className="modal__title">Delete Complaint</h3>
        <p className="modal__message">
          Permanently delete <strong>{complaint.complaintNumber}</strong> — “{complaint.title}”?
          Its status history, messages, feedback and attachments will be removed too.
          <strong> This cannot be undone.</strong>
        </p>
        {error && <div className="alert alert--error">{error}</div>}
        <div className="form-group">
          <label>Your Admin Password (to confirm it's you)</label>
          <input
            type="password"
            className="form-control"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Your own login password"
            autoFocus
            required
          />
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>Cancel</button>
          <button type="submit" className="btn btn--danger" disabled={submitting}>
            {submitting ? 'Deleting…' : 'Delete Permanently'}
          </button>
        </div>
      </form>
    </div>
  );
}
