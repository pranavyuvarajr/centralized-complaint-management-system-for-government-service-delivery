import { useState, useEffect, useCallback } from 'react';
import { complaintAPI } from '../services/api';
import ComplaintCard from '../components/ComplaintCard';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
import ExportCsvButton from '../components/ExportCsvButton';
import useDebounce from '../hooks/useDebounce';

const STATUS_OPTIONS = ['ASSIGNED', 'IN_PROGRESS', 'RESOLVED'];
const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export default function OfficialDashboard() {
  const [stats, setStats] = useState(null);
  const [complaints, setComplaints] = useState([]);
  const [pagination, setPagination] = useState({ page: 1, totalPages: 1, total: 0 });
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 400);
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    complaintAPI.officialStats().then((res) => setStats(res.data)).catch((err) => {
      setError(err.response?.data?.error || '');
    });
  }, []);

  const loadComplaints = useCallback((page = 1) => {
    setLoading(true);
    complaintAPI.getAll({
      page, limit: 8,
      search: debouncedSearch || undefined,
      status: status || undefined,
      priority: priority || undefined,
    })
      .then((res) => {
        setComplaints(res.data.data);
        setPagination(res.data.pagination);
      })
      .catch((err) => setError(err.response?.data?.error || 'Failed to load complaints'))
      .finally(() => setLoading(false));
  }, [debouncedSearch, status, priority]);

  useEffect(() => { loadComplaints(1); }, [loadComplaints]);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Department Queue</h1>
          <p className="subtitle">Complaints assigned to your department.</p>
        </div>
      </div>

      {error && <div className="alert alert--info">{error}</div>}

      {stats && (
        <div className="stats-grid">
          <div className="stat-card"><div className="stat-card__value">{stats.assigned}</div><div className="stat-card__label">Assigned</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.pendingReview}</div><div className="stat-card__label">Pending Start</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.inProgress}</div><div className="stat-card__label">In Progress</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.resolved}</div><div className="stat-card__label">Resolved</div></div>
          <div className="stat-card" style={{ borderLeftColor: 'var(--c-danger)' }}><div className="stat-card__value">{stats.overdue}</div><div className="stat-card__label">Overdue (5+ days)</div></div>
        </div>
      )}

      <div className="toolbar">
        <input type="text" className="form-control" placeholder="Search complaints…"
          value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="form-control" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
        </select>
        <select className="form-control" value={priority} onChange={(e) => setPriority(e.target.value)}>
          <option value="">All priorities</option>
          {PRIORITY_OPTIONS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <ExportCsvButton rows={complaints} filenamePrefix="department-queue" />
      </div>

      {loading ? (
        <div className="loading-block">Loading complaints…</div>
      ) : complaints.length === 0 ? (
        <EmptyState icon="✅" title="No complaints match your filters" subtitle="Your queue is clear, or try adjusting the filters." />
      ) : (
        <>
          {complaints.map((c) => <ComplaintCard key={c.id} complaint={c} basePath="/official" />)}
          <Pagination page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onChange={loadComplaints} />
        </>
      )}
    </div>
  );
}
