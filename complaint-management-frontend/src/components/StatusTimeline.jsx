const STATUS_META = {
  SUBMITTED: { icon: '📝', label: 'Submitted' },
  UNDER_REVIEW: { icon: '🔎', label: 'Under Review' },
  ASSIGNED: { icon: '📌', label: 'Assigned' },
  IN_PROGRESS: { icon: '⚙️', label: 'In Progress' },
  RESOLVED: { icon: '✅', label: 'Resolved' },
  CLOSED: { icon: '🔒', label: 'Closed' },
  REJECTED: { icon: '✖️', label: 'Rejected' },
};

function formatDuration(ms) {
  if (ms < 0) ms = 0;
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (ms < hour) {
    const mins = Math.max(1, Math.round(ms / minute));
    return `${mins} min${mins === 1 ? '' : 's'}`;
  }
  if (ms < day) {
    const hrs = Math.round(ms / hour);
    return `${hrs} hr${hrs === 1 ? '' : 's'}`;
  }
  const days = Math.round(ms / day);
  return `${days} day${days === 1 ? '' : 's'}`;
}

function formatRelative(date) {
  const diff = Date.now() - date.getTime();
  if (diff < 60000) return 'just now';
  return `${formatDuration(diff)} ago`;
}

/**
 * Renders a complaint's status history as a vertical timeline, newest first.
 * Shows an icon per status, who made the change, when, how long the
 * complaint spent in the previous stage, and any remarks left.
 *
 * `history` is expected in chronological (oldest-first) order, matching
 * what the API returns.
 */
export default function StatusTimeline({ history }) {
  if (!history || history.length === 0) {
    return <p style={{ fontSize: '0.85rem', color: 'var(--c-text-faint)' }}>No status changes recorded yet.</p>;
  }

  // Sort ascending defensively, then compute per-step duration, then reverse for display.
  const chronological = history.slice().sort((a, b) => new Date(a.updatedAt) - new Date(b.updatedAt));

  const withDurations = chronological.map((h, idx) => {
    const prev = chronological[idx - 1];
    const durationMs = prev ? new Date(h.updatedAt) - new Date(prev.updatedAt) : null;
    return { ...h, durationMs };
  });

  const display = withDurations.slice().reverse();
  const latestId = display[0]?.id;

  return (
    <ol className="status-timeline">
      {display.map((h, idx) => {
        const meta = STATUS_META[h.status] || { icon: '•', label: h.status };
        const date = new Date(h.updatedAt);
        const isCurrent = h.id === latestId;
        const isLast = idx === display.length - 1;
        return (
          <li key={h.id ?? `${h.status}-${h.updatedAt}`} className={`status-timeline__item${isCurrent ? ' is-current' : ''}`}>
            <span className={`status-timeline__dot status-timeline__dot--${h.status?.toLowerCase()}`} aria-hidden="true">
              {meta.icon}
            </span>
            <div className="status-timeline__content">
              <div className="status-timeline__row">
                <span className="status-timeline__label">{meta.label}</span>
                {isCurrent && <span className="status-timeline__current-tag">Current</span>}
              </div>
              {h.remarks && <p className="status-timeline__remarks">{h.remarks}</p>}
              <div className="status-timeline__meta">
                <span title={date.toLocaleString()}>{date.toLocaleString()}</span>
                {h.updatedBy && <span> · by {h.updatedBy}</span>}
              </div>
              {!isLast && h.durationMs != null && (
                <div className="status-timeline__duration">
                  Spent {formatDuration(h.durationMs)} in the previous stage
                </div>
              )}
              {isCurrent && (
                <div className="status-timeline__duration status-timeline__duration--live">
                  {formatRelative(date)}
                </div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
