export default function PriorityBadge({ priority }) {
  if (!priority) return null;
  const cls = priority.toLowerCase();
  return <span className={`badge badge--${cls}`}>{priority}</span>;
}
