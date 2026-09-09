import { useState, useEffect } from 'react';
import { adminAPI } from '../services/api';

export default function AdminDashboard() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    adminAPI.getStats().then((res) => setStats(res.data)).catch((err) => setError(err.response?.data?.error || 'Failed to load stats'));
  }, []);

  if (error) return <div className="alert alert--error">{error}</div>;
  if (!stats) return <div className="loading-block">Loading dashboard…</div>;

  const maxMonthly = Math.max(1, ...Object.values(stats.monthlyVolume));
  const maxCategory = Math.max(1, ...Object.values(stats.byCategory));

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>System Overview</h1>
          <p className="subtitle">Live statistics computed from current complaint records.</p>
        </div>
      </div>

      <div className="stats-grid">
        <div className="stat-card"><div className="stat-card__value">{stats.totalComplaints}</div><div className="stat-card__label">Total Complaints</div></div>
        <div className="stat-card"><div className="stat-card__value">{stats.submitted + stats.underReview}</div><div className="stat-card__label">Pending Review</div></div>
        <div className="stat-card"><div className="stat-card__value">{stats.assigned + stats.inProgress}</div><div className="stat-card__label">In Progress</div></div>
        <div className="stat-card"><div className="stat-card__value">{stats.resolved}</div><div className="stat-card__label">Resolved</div></div>
        <div className="stat-card"><div className="stat-card__value">{stats.closed}</div><div className="stat-card__label">Closed</div></div>
        <div className="stat-card" style={{ borderLeftColor: 'var(--c-danger)' }}><div className="stat-card__value">{stats.rejected}</div><div className="stat-card__label">Rejected</div></div>
      </div>

      <div className="form-row">
        <div className="card">
          <h3 className="card__title">Complaints by Category</h3>
          {Object.entries(stats.byCategory).length === 0 ? (
            <p style={{ fontSize: '0.85rem', color: 'var(--c-text-faint)' }}>No data yet.</p>
          ) : (
            Object.entries(stats.byCategory).map(([name, count]) => (
              <div key={name} style={{ marginBottom: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: 4 }}>
                  <span>{name}</span><span>{count}</span>
                </div>
                <div style={{ height: 8, background: 'var(--c-bg)', borderRadius: 4 }}>
                  <div style={{ width: `${(count / maxCategory) * 100}%`, height: '100%', background: 'var(--c-primary)', borderRadius: 4 }} />
                </div>
              </div>
            ))
          )}
        </div>

        <div className="card">
          <h3 className="card__title">Monthly Complaint Volume</h3>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 140 }}>
            {Object.entries(stats.monthlyVolume).map(([month, count]) => (
              <div key={month} style={{ flex: 1, textAlign: 'center' }}>
                <div style={{
                  height: `${Math.max(4, (count / maxMonthly) * 110)}px`,
                  background: 'var(--c-accent)', borderRadius: '4px 4px 0 0', margin: '0 auto 6px',
                }} />
                <div style={{ fontSize: '0.65rem', color: 'var(--c-text-faint)' }}>{month}</div>
                <div style={{ fontSize: '0.72rem', fontWeight: 700 }}>{count}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="card">
        <h3 className="card__title">Complaints by Department</h3>
        <div className="table-wrapper">
          <table>
            <thead><tr><th>Department</th><th>Complaints</th></tr></thead>
            <tbody>
              {Object.entries(stats.byDepartment).length === 0 ? (
                <tr><td colSpan={2} style={{ color: 'var(--c-text-faint)' }}>No complaints assigned yet.</td></tr>
              ) : Object.entries(stats.byDepartment).map(([name, count]) => (
                <tr key={name}><td>{name}</td><td>{count}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
