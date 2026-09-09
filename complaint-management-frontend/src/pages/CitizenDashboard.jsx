import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { complaintAPI } from '../services/api';
import ComplaintCard from '../components/ComplaintCard';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
import ExportCsvButton from '../components/ExportCsvButton';
import useDebounce from '../hooks/useDebounce';

const STATUS_OPTIONS = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];

export default function CitizenDashboard() {
  const [stats, setStats] = useState(null);
  const [complaints, setComplaints] = useState([]);
  const [pagination, setPagination] = useState({ page: 1, totalPages: 1, total: 0 });
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 400);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    complaintAPI.citizenStats().then((res) => setStats(res.data)).catch(() => {});
  }, []);

  const loadComplaints = useCallback((page = 1) => {
    setLoading(true);
    complaintAPI.getAll({ page, limit: 6, search: debouncedSearch || undefined, status: status || undefined })
      .then((res) => {
        setComplaints(res.data.data);
        setPagination(res.data.pagination);
      })
      .finally(() => setLoading(false));
  }, [debouncedSearch, status]);

  useEffect(() => { loadComplaints(1); }, [loadComplaints]);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>My Complaints</h1>
          <p className="subtitle">Track and manage complaints you've submitted.</p>
        </div>
        <Link to="/citizen/new" className="btn btn--accent">➕ New Complaint</Link>
      </div>

      {stats && (
        <div className="stats-grid">
          <div className="stat-card"><div className="stat-card__value">{stats.total}</div><div className="stat-card__label">Total</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.pending}</div><div className="stat-card__label">Pending</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.inProgress}</div><div className="stat-card__label">In Progress</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.resolved}</div><div className="stat-card__label">Resolved</div></div>
          <div className="stat-card"><div className="stat-card__value">{stats.closed}</div><div className="stat-card__label">Closed</div></div>
        </div>
      )}

      <div className="toolbar">
        <input type="text" className="form-control" placeholder="Search your complaints…"
          value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="form-control" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
        </select>
        <ExportCsvButton rows={complaints} filenamePrefix="my-complaints" />
      </div>

      {loading ? (
        <div className="loading-block">Loading complaints…</div>
      ) : complaints.length === 0 ? (
        <EmptyState icon="📭" title="No complaints found"
          subtitle="Try adjusting your filters, or submit a new complaint."
          action={<Link to="/citizen/new" className="btn btn--primary btn--sm">Submit a Complaint</Link>} />
      ) : (
        <>
          {complaints.map((c) => <ComplaintCard key={c.id} complaint={c} basePath="/citizen" />)}
          <Pagination page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onChange={loadComplaints} />
        </>
      )}
    </div>
  );
}
