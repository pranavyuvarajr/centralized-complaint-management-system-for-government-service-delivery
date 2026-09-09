import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  complaintAPI, commentAPI, feedbackAPI, attachmentAPI, adminAPI, departmentAPI,
} from '../services/api';
import { useAuth } from '../context/AuthContext';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import StatusTimeline from '../components/StatusTimeline';

const NEXT_STATUS = {
  SUBMITTED: ['UNDER_REVIEW', 'REJECTED'],
  UNDER_REVIEW: ['ASSIGNED', 'REJECTED'],
  ASSIGNED: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
  REJECTED: [],
};

export default function ComplaintDetails() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [complaint, setComplaint] = useState(null);
  const [comments, setComments] = useState([]);
  const [attachments, setAttachments] = useState([]);
  const [feedback, setFeedback] = useState(null);
  const [departments, setDepartments] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const [statusForm, setStatusForm] = useState({ status: '', remarks: '', resolutionInfo: '' });
  const [assignForm, setAssignForm] = useState({ departmentId: '', officialId: '' });
  const [officials, setOfficials] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [ratingForm, setRatingForm] = useState({ rating: 0, comment: '' });
  const [newFile, setNewFile] = useState(null);
  const [preview, setPreview] = useState(null); // { url, name } for image lightbox

  const basePath = user.role === 'ADMIN' ? '/admin' : user.role === 'OFFICIAL' ? '/official' : '/citizen';

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await complaintAPI.getById(id);
      setComplaint(data);
      setStatusForm({ status: '', remarks: '', resolutionInfo: data.resolutionInfo || '' });
      setAssignForm({ departmentId: data.departmentId || '', officialId: data.assignedOfficialId || '' });

      const [commentsRes, attachmentsRes] = await Promise.all([
        commentAPI.getAll(id).catch(() => ({ data: [] })),
        attachmentAPI.getAll(id).catch(() => ({ data: [] })),
      ]);
      setComments(commentsRes.data);
      setAttachments(attachmentsRes.data);

      if (data.status === 'RESOLVED' || data.status === 'CLOSED') {
        feedbackAPI.get(id).then((res) => setFeedback(res.data)).catch(() => setFeedback(null));
      }
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load complaint');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (user.role === 'ADMIN') {
      departmentAPI.getAll().then((res) => setDepartments(res.data)).catch(() => {});
    }
  }, [user.role]);

  useEffect(() => {
    if (user.role === 'ADMIN' && assignForm.departmentId) {
      adminAPI.getUsersByRole('OFFICIAL')
        .then((res) => setOfficials(res.data.filter((o) => o.departmentName ===
          departments.find((d) => String(d.id) === String(assignForm.departmentId))?.name)))
        .catch(() => setOfficials([]));
    }
  }, [assignForm.departmentId, departments, user.role]);

  const handleStatusUpdate = async (e) => {
    e.preventDefault();
    if (!statusForm.status) return;
    setActionLoading(true);
    try {
      await complaintAPI.updateStatus(id, statusForm);
      await load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update status');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAssign = async (e) => {
    e.preventDefault();
    if (!assignForm.departmentId) return;
    setActionLoading(true);
    try {
      await adminAPI.assignComplaint(id, {
        departmentId: Number(assignForm.departmentId),
        officialId: assignForm.officialId ? Number(assignForm.officialId) : null,
      });
      await load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to assign complaint');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePriority = async (priority) => {
    setActionLoading(true);
    try {
      if (user.role === 'ADMIN') await adminAPI.updatePriority(id, priority);
      else await complaintAPI.updatePriority(id, priority);
      await load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to update priority');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReopen = async () => {
    const reason = window.prompt('Briefly explain why you are reopening this complaint (optional):') || '';
    setActionLoading(true);
    try {
      await complaintAPI.reopen(id, reason);
      await load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to reopen complaint');
    } finally {
      setActionLoading(false);
    }
  };

  const handleClose = async () => {
    if (!window.confirm('Close this resolved complaint?')) return;
    setActionLoading(true);
    try {
      await complaintAPI.close(id);
      await load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to close complaint');
    } finally {
      setActionLoading(false);
    }
  };

  const handleComment = async (e) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    setActionLoading(true);
    try {
      await commentAPI.add(id, commentText.trim());
      setCommentText('');
      const res = await commentAPI.getAll(id);
      setComments(res.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to post message');
    } finally {
      setActionLoading(false);
    }
  };

  const handleFeedback = async (e) => {
    e.preventDefault();
    if (!ratingForm.rating) return;
    setActionLoading(true);
    try {
      await feedbackAPI.submit(id, ratingForm);
      const res = await feedbackAPI.get(id);
      setFeedback(res.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to submit feedback');
    } finally {
      setActionLoading(false);
    }
  };

  const handleFileUpload = async (e) => {
    e.preventDefault();
    if (!newFile) return;
    setActionLoading(true);
    try {
      await attachmentAPI.upload(id, newFile);
      setNewFile(null);
      const res = await attachmentAPI.getAll(id);
      setAttachments(res.data);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to upload attachment');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDownload = async (attachment) => {
    try {
      const res = await attachmentAPI.download(attachment.id);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.originalFileName;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch {
      setError('Failed to download attachment');
    }
  };

  const handleView = async (attachment) => {
    try {
      const res = await attachmentAPI.download(attachment.id);
      const blob = new Blob([res.data], { type: attachment.contentType });
      const url = window.URL.createObjectURL(blob);
      if (attachment.contentType?.startsWith('image/')) {
        setPreview({ url, name: attachment.originalFileName });
      } else {
        // PDFs and other viewable types: open in a new tab, rendered by the browser
        window.open(url, '_blank', 'noopener,noreferrer');
      }
    } catch {
      setError('Failed to open attachment');
    }
  };

  const closePreview = () => {
    if (preview?.url) window.URL.revokeObjectURL(preview.url);
    setPreview(null);
  };

  if (loading) return <div className="loading-block">Loading complaint…</div>;
  if (!complaint) return <div className="alert alert--error">{error || 'Complaint not found'}</div>;

  const isOwner = user.role === 'CITIZEN' && complaint.citizenEmail === user.email;
  const canManage = user.role === 'OFFICIAL' || user.role === 'ADMIN';
  const nextStatuses = NEXT_STATUS[complaint.status] || [];

  return (
    <div>
      <button className="btn btn--ghost btn--sm" style={{ marginBottom: 16 }} onClick={() => navigate(`${basePath}`)}>
        ← Back
      </button>

      {error && <div className="alert alert--error">{error}</div>}

      <div className="card">
        <div className="page-header" style={{ marginBottom: 12 }}>
          <div>
            <h1 style={{ fontSize: '1.2rem' }}>{complaint.title}</h1>
            <p className="subtitle">{complaint.complaintNumber}</p>
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            <PriorityBadge priority={complaint.priority} />
            <StatusBadge status={complaint.status} />
          </div>
        </div>

        <div className="form-row" style={{ marginBottom: 16, fontSize: '0.85rem' }}>
          <div><span className="card__label">Category</span><br />{complaint.category}</div>
          <div><span className="card__label">Location</span><br />{complaint.location || '—'}</div>
          <div><span className="card__label">Department</span><br />{complaint.departmentName || 'Not yet assigned'}</div>
          <div><span className="card__label">Assigned Official</span><br />{complaint.assignedOfficialName || '—'}</div>
          {canManage && <div><span className="card__label">Citizen</span><br />{complaint.citizenName} ({complaint.citizenEmail})</div>}
          <div><span className="card__label">Submitted</span><br />{new Date(complaint.createdAt).toLocaleString()}</div>
          {complaint.resolvedAt && <div><span className="card__label">Resolved</span><br />{new Date(complaint.resolvedAt).toLocaleString()}</div>}
          {complaint.closedAt && <div><span className="card__label">Closed</span><br />{new Date(complaint.closedAt).toLocaleString()}</div>}
        </div>

        <span className="card__label">Description</span>
        <p style={{ marginTop: 6, fontSize: '0.9rem' }}>{complaint.description}</p>

        {complaint.resolutionInfo && (
          <div className="alert alert--success" style={{ marginTop: 16 }}>
            <strong>Resolution:</strong> {complaint.resolutionInfo}
          </div>
        )}
      </div>

      {/* Attachments */}
      <div className="card">
        <h3 className="card__title">Attachments</h3>
        {attachments.length === 0 ? (
          <p style={{ fontSize: '0.85rem', color: 'var(--c-text-faint)' }}>No attachments yet.</p>
        ) : (
          <div className="attachment-list">
            {attachments.map((a) => (
              <div className="attachment-item" key={a.id}>
                <span>📎 {a.originalFileName} <span style={{ color: 'var(--c-text-faint)' }}>({Math.round(a.fileSize / 1024)} KB)</span></span>
                <span style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn--ghost btn--sm" onClick={() => handleView(a)}>View</button>
                  <button className="btn btn--outline btn--sm" onClick={() => handleDownload(a)}>Download</button>
                </span>
              </div>
            ))}
          </div>
        )}
        {isOwner && (
          <form onSubmit={handleFileUpload} style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <input type="file" className="form-control" accept=".jpg,.jpeg,.png,.pdf"
              onChange={(e) => setNewFile(e.target.files[0])} />
            <button type="submit" className="btn btn--outline btn--sm" disabled={!newFile || actionLoading}>Upload</button>
          </form>
        )}
      </div>

      {/* Official/Admin actions */}
      {canManage && (
        <div className="card">
          <h3 className="card__title">Manage Complaint</h3>

          {user.role === 'ADMIN' && (
            <form onSubmit={handleAssign} style={{ marginBottom: 20 }}>
              <span className="card__label">Assignment</span>
              <div className="form-row" style={{ marginTop: 8 }}>
                <select className="form-control" value={assignForm.departmentId}
                  onChange={(e) => setAssignForm({ departmentId: e.target.value, officialId: '' })}>
                  <option value="">Select department</option>
                  {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                </select>
                <select className="form-control" value={assignForm.officialId}
                  onChange={(e) => setAssignForm({ ...assignForm, officialId: e.target.value })}
                  disabled={!assignForm.departmentId}>
                  <option value="">Unassigned official</option>
                  {officials.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                </select>
                <button type="submit" className="btn btn--primary" disabled={actionLoading}>Assign</button>
              </div>
            </form>
          )}

          <div style={{ marginBottom: 20 }}>
            <span className="card__label">Priority</span>
            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
              {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => (
                <button key={p} type="button"
                  className={`chip ${complaint.priority === p ? 'active' : ''}`}
                  disabled={actionLoading}
                  onClick={() => handlePriority(p)}>{p}</button>
              ))}
            </div>
          </div>

          {nextStatuses.length > 0 ? (
            <form onSubmit={handleStatusUpdate}>
              <span className="card__label">Update Status</span>
              <div className="form-row" style={{ marginTop: 8, marginBottom: 10 }}>
                <select className="form-control" value={statusForm.status}
                  onChange={(e) => setStatusForm({ ...statusForm, status: e.target.value })}>
                  <option value="">Select new status</option>
                  {nextStatuses.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Remarks</label>
                <input type="text" className="form-control" placeholder="Add a note about this update"
                  value={statusForm.remarks} onChange={(e) => setStatusForm({ ...statusForm, remarks: e.target.value })} />
              </div>
              {statusForm.status === 'RESOLVED' && (
                <div className="form-group">
                  <label>Resolution details</label>
                  <textarea className="form-control" rows={3}
                    placeholder="Explain what was done to resolve this complaint"
                    value={statusForm.resolutionInfo}
                    onChange={(e) => setStatusForm({ ...statusForm, resolutionInfo: e.target.value })} />
                </div>
              )}
              <button type="submit" className="btn btn--success" disabled={!statusForm.status || actionLoading}>
                Update Status
              </button>
            </form>
          ) : (
            <p style={{ fontSize: '0.82rem', color: 'var(--c-text-faint)' }}>
              This complaint is in a final state and cannot be updated further.
            </p>
          )}
        </div>
      )}

      {/* Citizen actions */}
      {isOwner && complaint.status === 'RESOLVED' && (
        <div className="card">
          <h3 className="card__title">Not satisfied?</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--c-text-light)', marginBottom: 12 }}>
            You can close this complaint if you're happy with the resolution, or reopen it if the issue persists.
          </p>
          <div style={{ display: 'flex', gap: 10 }}>
            <button className="btn btn--success" onClick={handleClose} disabled={actionLoading}>Close Complaint</button>
            <button className="btn btn--outline" onClick={handleReopen} disabled={actionLoading}>Reopen</button>
          </div>
        </div>
      )}

      {isOwner && (complaint.status === 'RESOLVED' || complaint.status === 'CLOSED') && (
        <div className="card">
          <h3 className="card__title">Your Feedback</h3>
          {feedback ? (
            <div>
              <div className="star-rating">
                {[1, 2, 3, 4, 5].map((n) => <span key={n} className={n <= feedback.rating ? 'filled' : ''} style={{ color: n <= feedback.rating ? 'var(--c-accent)' : 'var(--c-border)' }}>★</span>)}
              </div>
              {feedback.comment && <p style={{ fontSize: '0.85rem', marginTop: 8 }}>{feedback.comment}</p>}
            </div>
          ) : (
            <form onSubmit={handleFeedback}>
              <div className="star-rating" style={{ marginBottom: 12 }}>
                {[1, 2, 3, 4, 5].map((n) => (
                  <button type="button" key={n}
                    className={n <= ratingForm.rating ? 'filled' : ''}
                    onClick={() => setRatingForm({ ...ratingForm, rating: n })}>★</button>
                ))}
              </div>
              <textarea className="form-control" rows={2} placeholder="Optional comment"
                value={ratingForm.comment} onChange={(e) => setRatingForm({ ...ratingForm, comment: e.target.value })} />
              <button type="submit" className="btn btn--primary" style={{ marginTop: 10 }} disabled={!ratingForm.rating || actionLoading}>
                Submit Feedback
              </button>
            </form>
          )}
        </div>
      )}

      {/* Comments */}
      <div className="card">
        <h3 className="card__title">Messages</h3>
        {comments.length === 0 ? (
          <p style={{ fontSize: '0.85rem', color: 'var(--c-text-faint)', marginBottom: 14 }}>No messages yet.</p>
        ) : (
          <div style={{ marginBottom: 14 }}>
            {comments.map((c) => (
              <div className="comment" key={c.id}>
                <div className="comment__header">
                  <span>{c.authorName} <span className="comment__role">({c.authorRole.toLowerCase()})</span></span>
                </div>
                <div className="comment__body">{c.message}</div>
                <div className="comment__meta">{new Date(c.createdAt).toLocaleString()}</div>
              </div>
            ))}
          </div>
        )}
        <form onSubmit={handleComment} style={{ display: 'flex', gap: 8 }}>
          <input type="text" className="form-control" placeholder="Write a message…"
            value={commentText} onChange={(e) => setCommentText(e.target.value)} />
          <button type="submit" className="btn btn--primary btn--sm" disabled={!commentText.trim() || actionLoading}>Send</button>
        </form>
      </div>

      {/* History */}
      <div className="card">
        <h3 className="card__title">Status History</h3>
        <StatusTimeline history={complaint.history} />
      </div>

      {preview && (
        <div className="lightbox" onClick={closePreview}>
          <button className="lightbox__close" onClick={closePreview} aria-label="Close">✕</button>
          <img
            src={preview.url}
            alt={preview.name}
            className="lightbox__img"
            onClick={(e) => e.stopPropagation()}
          />
          <div className="lightbox__caption">{preview.name}</div>
        </div>
      )}
    </div>
  );
}
