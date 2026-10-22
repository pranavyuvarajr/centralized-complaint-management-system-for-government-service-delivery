import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authAPI } from '../services/api';

/** Public page opened from the verification email: /verify-alert-email?token=... */
export default function VerifyAlertEmail() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [state, setState] = useState(token ? 'loading' : 'error');
  const [message, setMessage] = useState(token ? '' : 'This verification link is incomplete.');
  const started = useRef(false); // the token is single-use: never call twice (e.g. StrictMode double effects)

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    authAPI.verifyAlertEmail(token)
      .then(() => setState('success'))
      .catch((err) => {
        setState('error');
        setMessage(err.response?.data?.error || 'Verification failed. Please try again later.');
      });
  }, [token]);

  return (
    <div style={{ maxWidth: 520, margin: '60px auto', padding: '0 16px' }}>
      <div className="card">
        <h2 className="card__title">Alert email verification</h2>
        {state === 'loading' && <p>Verifying your alert email…</p>}
        {state === 'success' && (
          <div className="alert alert--success">
            Your alert email is verified. Urgent-complaint alerts will now be sent to it.
          </div>
        )}
        {state === 'error' && <div className="alert alert--error">{message}</div>}
        <Link className="btn btn--primary" to="/">Go to the application</Link>
      </div>
    </div>
  );
}
