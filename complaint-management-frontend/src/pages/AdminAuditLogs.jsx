import { useState, useEffect, useCallback } from 'react';
import { adminAPI } from '../services/api';
import Pagination from '../components/Pagination';

export default function AdminAuditLogs() {
  const [logs, setLogs] = useState([]);
  const [pagination, setPagination] = useState({ page: 1, totalPages: 1, total: 0 });
  const [loading, setLoading] = useState(true);

  const load = useCallback((page = 1) => {
    setLoading(true);
    adminAPI.getAuditLogs({ page, limit: 15 })
      .then((res) => {
        setLogs(res.data.data);
        setPagination(res.data.pagination);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(1); }, [load]);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Audit Logs</h1>
          <p className="subtitle">A record of significant administrative and workflow actions.</p>
        </div>
      </div>

      <div className="card">
        {loading ? <div className="loading-block">Loading logs…</div> : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>When</th><th>Performed By</th><th>Action</th><th>Details</th></tr></thead>
              <tbody>
                {logs.length === 0 ? (
                  <tr><td colSpan={4} style={{ color: 'var(--c-text-faint)' }}>No audit entries yet.</td></tr>
                ) : logs.map((l) => (
                  <tr key={l.id}>
                    <td>{new Date(l.createdAt).toLocaleString()}</td>
                    <td>{l.performedBy}</td>
                    <td><span className="badge badge--assigned">{l.action.replace(/_/g, ' ')}</span></td>
                    <td>{l.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination page={pagination.page} totalPages={pagination.totalPages} total={pagination.total} onChange={load} />
      </div>
    </div>
  );
}
