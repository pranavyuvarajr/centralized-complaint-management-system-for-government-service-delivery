export default function Pagination({ page, totalPages, total, onChange }) {
  if (totalPages <= 1) return null;

  const pages = [];
  const start = Math.max(1, page - 2);
  const end = Math.min(totalPages, start + 4);
  for (let p = start; p <= end; p++) pages.push(p);

  return (
    <div>
      <div className="pagination">
        <button className="pagination__btn" disabled={page <= 1} onClick={() => onChange(page - 1)}>‹</button>
        {start > 1 && <span style={{ color: 'var(--c-text-faint)' }}>…</span>}
        {pages.map((p) => (
          <button
            key={p}
            className={`pagination__btn ${p === page ? 'active' : ''}`}
            onClick={() => onChange(p)}
          >
            {p}
          </button>
        ))}
        {end < totalPages && <span style={{ color: 'var(--c-text-faint)' }}>…</span>}
        <button className="pagination__btn" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>›</button>
      </div>
      <div className="pagination__info">Page {page} of {totalPages} • {total} total</div>
    </div>
  );
}
