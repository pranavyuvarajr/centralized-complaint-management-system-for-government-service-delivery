import { useState } from 'react';

const MIN_REASON = 10;
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

/**
 * Dialog for a citizen reopening their resolved complaint. A reason is required
 * (so the department knows what is still wrong); a photo is optional.
 *
 * props:
 *  - complaint: { complaintNumber, title }
 *  - onConfirm: (reason, photoFile | null) => Promise  (throw to show the error inside the dialog)
 *  - onCancel: () => void
 */
export default function ReopenModal({ complaint, onConfirm, onCancel }) {
  const [reason, setReason] = useState('');
  const [photo, setPhoto] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handlePhoto = (e) => {
    const file = e.target.files[0] || null;
    setError('');
    if (file && !['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Only JPG or PNG photos are allowed.');
      e.target.value = '';
      setPhoto(null);
      return;
    }
    if (file && file.size > MAX_PHOTO_BYTES) {
      setError('The photo is larger than 5MB.');
      e.target.value = '';
      setPhoto(null);
      return;
    }
    setPhoto(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (reason.trim().length < MIN_REASON) {
      setError(`Please explain what is still wrong (at least ${MIN_REASON} characters).`);
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm(reason.trim(), photo);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to reopen the complaint.');
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={submitting ? undefined : onCancel}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h3 className="modal__title">Reopen Complaint</h3>
        <p className="modal__message">
          Reopen <strong>{complaint.complaintNumber}</strong>? It goes back to the department as In Progress.
          Tell them what is still wrong, and add a photo if it helps.
        </p>
        {error && <div className="alert alert--error">{error}</div>}
        <div className="form-group">
          <label>What is still wrong?</label>
          <textarea className="form-control" rows={3} autoFocus
            placeholder="e.g. The pothole was patched but the surface has already cracked again."
            value={reason} onChange={(e) => setReason(e.target.value)} />
          <span className="hint">At least {MIN_REASON} characters. Currently {reason.trim().length}.</span>
        </div>
        <div className="form-group">
          <label>Photo (optional)</label>
          <input type="file" className="form-control" accept="image/jpeg,image/png" onChange={handlePhoto} />
          <span className="hint">JPG or PNG, up to 5MB.</span>
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={submitting}>Cancel</button>
          <button type="submit" className="btn btn--primary" disabled={submitting}>
            {submitting ? 'Reopening…' : 'Reopen Complaint'}
          </button>
        </div>
      </form>
    </div>
  );
}
