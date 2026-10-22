import { useState } from 'react';
import { profileAPI } from '../services/api';

/**
 * Alert email for officials and admins: urgent-complaint emails go ONLY to a verified alert address.
 * With none set (or an unverified one) alerts arrive as in-app notifications instead.
 */
export default function AlertEmailCard({ profile, onChanged }) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const has = !!profile.alertEmail;
  const verified = has && profile.alertEmailVerified;

  const run = async (call, okMessage) => {
    setBusy(true); setMessage(''); setError('');
    try {
      await call();
      setMessage(okMessage);
      setValue('');
      await onChanged();
    } catch (err) {
      setError(err.response?.data?.error || 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const save = (e) => {
    e.preventDefault();
    run(() => profileAPI.setAlertEmail(value.trim()), 'Verification email sent. Open the link in it to activate this address.');
  };

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h3 className="card__title">Alert Email</h3>
      <p className="hint" style={{ marginTop: 0 }}>
        Urgent complaints are emailed to this address. Only a verified address is used; until then you will
        receive these alerts as in-app notifications.
      </p>
      {message && <div className="alert alert--success">{message}</div>}
      {error && <div className="alert alert--error">{error}</div>}

      {has && (
        <div className="form-group">
          <label>Current alert email</label>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <strong>{profile.alertEmail}</strong>
            <span className={`badge ${verified ? 'badge--resolved' : 'badge--under_review'}`}>
              {verified ? 'Verified' : 'Pending verification'}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
            {!verified && (
              <button type="button" className="btn btn--ghost btn--sm" disabled={busy}
                onClick={() => run(() => profileAPI.resendAlertEmail(), 'Verification email sent again.')}>
                Resend verification email
              </button>
            )}
            <button type="button" className="btn btn--ghost btn--sm" disabled={busy}
              onClick={() => run(() => profileAPI.clearAlertEmail(), 'Alert email removed.')}>
              Remove
            </button>
          </div>
        </div>
      )}

      <form onSubmit={save}>
        <div className="form-group">
          <label>{has ? 'Change alert email' : 'Set alert email'}</label>
          <input type="email" className="form-control" required maxLength={254} placeholder="name@example.com"
            value={value} onChange={(e) => setValue(e.target.value)} />
          <span className="hint">Changing the address requires verifying the new one.</span>
        </div>
        <button type="submit" className="btn btn--primary" disabled={busy || !value.trim()}>
          {has ? 'Save new address' : 'Save and send verification'}
        </button>
      </form>
    </div>
  );
}
