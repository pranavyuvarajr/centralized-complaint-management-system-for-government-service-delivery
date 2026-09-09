import { useState, useEffect, useCallback } from 'react';
import { complaintAPI } from '../services/api';
import ComplaintCard from '../components/ComplaintCard';
import Pagination from '../components/Pagination';
import EmptyState from '../components/EmptyState';
import ExportCsvButton from '../components/ExportCsvButton';
import useDebounce from '../hooks/useDebounce';

const STATUS_OPTIONS = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export default function AdminComplaints() {
  const [complaints, setComplaints] = useState([]);
  const [pagination, setPagination] = useState({ page: 1, totalPages: 1, total: 0 });
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search, 400);
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback((page = 1) => {
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
      .finally(() => setLoading(false));
  }, [debouncedSearch, status, priority]);

  useEffect(() => { load(1); }, [load]);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>All Complaints</h1>
          <p className="subtitle">Assign, prioritize, and monitor every complaint in the system.</p>
        </div>
      </div>

      <div className="toolbar">
        <input type="text" className="form-control" placeholder="Search by title, description, or reference…"
          value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="form-control" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
        </select>
        <select className="form-control" value={priority} onChange={(e) => setPriority(e.target.value)}>
          <option value="">All priorities</option>
          {PRIORITY_OPTIONS.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
        <ExportCsvButton rows={complaints} filenamePrefix="all-complaints" />
      </div>

      {loading ? (
        <div className="loading-block">Loading complaints…</div>
      ) : complaints.length === 0 ? (
        <EmptyState icon="🔍" title="No complaints match your filters" />
      ) : (
        <>
          {complaints.map((c) => <ComplaintCard key={c.id} complaint={c} basePath="/admin" />)}
          <Pagination page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onChange={load} />
        </>
      )}
    </div>
  );
}
