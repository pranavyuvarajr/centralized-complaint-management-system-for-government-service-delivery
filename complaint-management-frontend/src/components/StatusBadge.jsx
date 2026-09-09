export default function StatusBadge({ status }) {
  const cls = status?.toLowerCase().replace(/ /g, '_');
  const label = status?.replace(/_/g, ' ');
  return <span className={`badge badge--${cls}`}>{label}</span>;
}
