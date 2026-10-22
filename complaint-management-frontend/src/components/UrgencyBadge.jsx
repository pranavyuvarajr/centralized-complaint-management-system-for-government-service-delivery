// Small badge for the urgent-complaint flow. Shown on cards/lists; nothing is rendered for normal
// complaints or for urgent ones that were downgraded to normal after verification.
export default function UrgencyBadge({ urgencyStatus }) {
  if (urgencyStatus === 'PENDING') {
    return <span className="badge badge--urgent badge--urgency-pending" title="Marked urgent by the citizen, awaiting verification">⚡ Urgent · pending</span>;
  }
  if (urgencyStatus === 'CONFIRMED_URGENT') {
    return <span className="badge badge--urgent" title="Urgency verified by staff">⚡ Urgent · verified</span>;
  }
  return null;
}
