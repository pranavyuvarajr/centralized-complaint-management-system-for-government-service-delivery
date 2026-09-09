import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { complaintAPI } from '../services/api';
import StatusBadge from '../components/StatusBadge';
import PriorityBadge from '../components/PriorityBadge';
import StatusTimeline from '../components/StatusTimeline';

const STAGES = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

export default function TrackComplaint() {
  const [params] = useSearchParams();
  const [number, setNumber] = useState(params.get('ref') || '');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSearch = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);
    if (!number.trim()) return;
    setLoading(true);
    try {
      const { data } = await complaintAPI.track(number.trim());
      setResult(data);
    } catch (err) {
      setError(err.response?.data?.error || 'No complaint found with that reference number.');
    } finally {
      setLoading(false);
    }
  };

  const currentIndex = result ? STAGES.indexOf(result.status) : -1;
  const isRejected = result?.status === 'REJECTED';

  return (
    <div className="auth-page" style={{ alignItems: 'flex-start', paddingTop: 60 }}>
      <div className="auth-card" style={{ maxWidth: 560 }}>
        <h1 className="auth-card__title">Track a Complaint</h1>
        <p className="auth-card__subtitle">Enter your complaint reference number (e.g. CMP-2026-000001)</p>

        <form onSubmit={handleSearch} style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <input
            type="text"
            className="form-control"
            placeholder="CMP-2026-000001"
            value={number}
            onChange={(e) => setNumber(e.target.value)}
          />
          <button type="submit" className="btn btn--primary" disabled={loading}>
            {loading ? 'Searching…' : 'Track'}
          </button>
        </form>

        {error && <div className="alert alert--error">{error}</div>}

        {result && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
              <div>
                <h3 style={{ fontSize: '1.05rem' }}>{result.title}</h3>
                <span style={{ fontSize: '0.78rem', color: 'var(--c-text-faint)' }}>{result.complaintNumber}</span>
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                <PriorityBadge priority={result.priority} />
                <StatusBadge status={result.status} />
              </div>
            </div>

            <div className="form-row" style={{ marginBottom: 20, fontSize: '0.85rem' }}>
              <div><strong style={{ fontSize: '0.72rem', color: 'var(--c-text-faint)' }}>CATEGORY</strong><br />{result.category}</div>
              <div><strong style={{ fontSize: '0.72rem', color: 'var(--c-text-faint)' }}>DEPARTMENT</strong><br />{result.departmentName || 'Not yet assigned'}</div>
              <div><strong style={{ fontSize: '0.72rem', color: 'var(--c-text-faint)' }}>SUBMITTED</strong><br />{new Date(result.submittedAt).toLocaleDateString()}</div>
            </div>

            {!isRejected ? (
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
                {STAGES.map((s, i) => (
                  <div key={s} style={{ textAlign: 'center', flex: 1 }}>
                    <div style={{
                      width: 16, height: 16, borderRadius: '50%', margin: '0 auto 6px',
                      background: i <= currentIndex ? 'var(--c-primary)' : 'var(--c-border)',
                    }} />
                    <div style={{ fontSize: '0.62rem', color: i <= currentIndex ? 'var(--c-text)' : 'var(--c-text-faint)', fontWeight: 600 }}>
                      {s.replace('_', ' ')}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="alert alert--error">This complaint was reviewed and marked as rejected.</div>
            )}

            <h4 style={{ fontSize: '0.85rem', marginBottom: 10 }}>History</h4>
            <StatusTimeline history={result.history} />
          </div>
        )}
      </div>
    </div>
  );
}
