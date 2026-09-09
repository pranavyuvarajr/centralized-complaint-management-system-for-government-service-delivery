export default function EmptyState({ icon = '📭', title, subtitle, action }) {
  return (
    <div className="card empty-state">
      <div className="empty-state__icon">{icon}</div>
      <p style={{ fontWeight: 600, color: 'var(--c-text)' }}>{title}</p>
      {subtitle && <p style={{ marginTop: 4 }}>{subtitle}</p>}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
}
