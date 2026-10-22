import { useState } from 'react';
import { complaintAPI, adminAPI } from '../services/api';

const NOTE_MIN = 10;

const fmt = (d) => (d ? new Date(d).toLocaleString() : '');

/**
 * Urgency panel on the complaint page (citizen / official / admin).
 * - PENDING: justification, plus (assigned official / admin only) the call link and the two actions.
 * - Decided: a permanent marker with who decided, when, and the reason.
 * - Admin: can override a locked decision (note mandatory).
 * The server decides what each caller may see or do (canDecideUrgency, canOverrideUrgency, citizenPhone).
 */
export default function UrgencyPanel({ complaint, onChanged }) {
  const [mode, setMode] = useState(''); // '' | 'normal' | 'override'
  const [note, setNote] = useState('');
  const [overrideTarget, setOverrideTarget] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const status = complaint.urgencyStatus || 'NONE';
  if (status === 'NONE' && !complaint.wasInitiallyUrgent) return null;

  const reset = () => { setMode(''); setNote(''); setOverrideTarget(''); setError(''); };

  const run = async (call) => {
    setBusy(true);
    setError('');
    try {
      await call();
      reset();
      await onChanged();
    } catch (err) {
      setError(err.response?.data?.error || 'Could not save the decision. Please try again.');
      // A decision may have been recorded by someone else first: show the current state.
      if (err.response?.status === 400) await onChanged();
    } finally {
      setBusy(false);
    }
  };

  const confirmUrgent = () => run(() => complaintAPI.decideUrgency(complaint.id, 'CONFIRM_URGENT', note.trim() || undefined));
  const markNormal = () => {
    if (note.trim().length < NOTE_MIN) {
      setError(`Please give a reason of at least ${NOTE_MIN} characters (for example: not urgent, or the citizen did not answer).`);
      return;
    }
    run(() => complaintAPI.decideUrgency(complaint.id, 'MARK_NORMAL', note.trim()));
  };
  const override = () => {
    if (!overrideTarget) { setError('Choose the new decision.'); return; }
    if (note.trim().length < NOTE_MIN) { setError(`A note of at least ${NOTE_MIN} characters is required.`); return; }
    run(() => adminAPI.overrideUrgency(complaint.id, overrideTarget, note.trim()));
  };

  const by = complaint.urgencyDecidedByName || 'staff';
  const when = fmt(complaint.urgencyDecidedAt);
  const reason = complaint.urgencyDecisionNote;

  let tone = '';
  let title;
  let body = null;
  if (status === 'PENDING') {
    title = '⚡ Urgent: pending verification';
  } else if (status === 'CONFIRMED_URGENT') {
    tone = 'urgency-panel--verified';
    title = complaint.urgencyOverridden
      ? `⚡ Urgent: set as verified by admin ${by} on ${when}`
      : `⚡ Verified urgent by ${by} on ${when}`;
    if (reason) body = `${complaint.urgencyOverridden ? 'Reason' : 'Note'}: ${reason}`;
  } else {
    tone = 'urgency-panel--normal';
    title = complaint.urgencyOverridden
      ? `Initially marked urgent, changed to normal by admin ${by} on ${when}. Reason: ${reason || '—'}`
      : `Initially marked urgent, changed to normal by ${by} on ${when}. Reason: ${reason || '—'}`;
  }

  return (
    <div className={`urgency-panel ${tone}`}>
      <div className="urgency-panel__title">{title}</div>
      {body && <div style={{ fontSize: '0.85rem' }}>{body}</div>}

      {complaint.urgencyReason && (
        <>
          <span className="card__label">Citizen's justification</span>
          <div className="urgency-panel__quote">{complaint.urgencyReason}</div>
        </>
      )}

      {complaint.citizenPhone && (
        <div>
          <span className="card__label">Citizen's mobile</span><br />
          <a className="urgency-panel__call" href={`tel:${complaint.citizenPhone}`}>📞 {complaint.citizenPhone}</a>
        </div>
      )}

      {status === 'PENDING' && !complaint.canDecideUrgency && (
        <p className="hint" style={{ margin: 0 }}>
          The assigned official (or an administrator) will verify this complaint and may call the citizen.
        </p>
      )}

      {error && <div className="alert alert--error" style={{ marginTop: 10 }}>{error}</div>}

      {status === 'PENDING' && complaint.canDecideUrgency && (
        <>
          <p className="hint" style={{ margin: '4px 0 0' }}>
            Call the citizen, then confirm the complaint as urgent or mark it as normal. The first decision is final.
          </p>
          {mode === 'normal' && (
            <div style={{ marginTop: 10 }}>
              <textarea className="form-control" rows={3} maxLength={500} autoFocus
                placeholder="Reason (required), e.g. not urgent, or the citizen could not be reached."
                value={note} onChange={(e) => setNote(e.target.value)} />
            </div>
          )}
          <div className="urgency-panel__actions">
            {mode !== 'normal' && (
              <button type="button" className="btn btn--success" disabled={busy} onClick={confirmUrgent}>Confirm as urgent</button>
            )}
            {mode !== 'normal'
              ? <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => { setMode('normal'); setNote(''); setError(''); }}>Mark as normal…</button>
              : (
                <>
                  <button type="button" className="btn btn--danger" disabled={busy} onClick={markNormal}>Mark as normal</button>
                  <button type="button" className="btn btn--ghost" disabled={busy} onClick={reset}>Cancel</button>
                </>
              )}
          </div>
        </>
      )}

      {complaint.canOverrideUrgency && (
        <div style={{ marginTop: 12 }}>
          {mode !== 'override' ? (
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => { setMode('override'); setNote(''); setError(''); }}>
              Admin: change decision…
            </button>
          ) : (
            <div>
              <select className="form-control" value={overrideTarget} onChange={(e) => setOverrideTarget(e.target.value)}>
                <option value="">New decision…</option>
                {status !== 'CONFIRMED_URGENT' && <option value="CONFIRM_URGENT">Verified urgent</option>}
                {status !== 'DOWNGRADED_TO_NORMAL' && <option value="MARK_NORMAL">Normal</option>}
              </select>
              <textarea className="form-control" rows={3} style={{ marginTop: 8 }} maxLength={500}
                placeholder="Note (required). The citizen is notified and this is recorded in the timeline."
                value={note} onChange={(e) => setNote(e.target.value)} />
              <div className="urgency-panel__actions">
                <button type="button" className="btn btn--primary" disabled={busy} onClick={override}>Save change</button>
                <button type="button" className="btn btn--ghost" disabled={busy} onClick={reset}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
