import { useNavigate } from 'react-router-dom';
import StatusBadge from './StatusBadge';
import PriorityBadge from './PriorityBadge';

export default function ComplaintCard({ complaint, basePath = '' }) {
  const navigate = useNavigate();

  return (
    <div
      className="complaint-card"
      onClick={() => navigate(`${basePath}/complaint/${complaint.id}`)}
    >
      <div className="complaint-card__header">
        <div>
          <div className="complaint-card__title">{complaint.title}</div>
          <span className="complaint-card__id">{complaint.complaintNumber || `#${complaint.id}`}</span>
        </div>
        <div className="complaint-card__badges">
          <PriorityBadge priority={complaint.priority} />
          <StatusBadge status={complaint.status} />
        </div>
      </div>
      <p className="complaint-card__desc">
        {complaint.description?.length > 130
          ? complaint.description.substring(0, 130) + '…'
          : complaint.description}
      </p>
      <div className="complaint-card__meta">
        <span>📁 {complaint.category}</span>
        {complaint.departmentName && <span>🏢 {complaint.departmentName}</span>}
        {complaint.location && <span>📍 {complaint.location}</span>}
        <span>{new Date(complaint.createdAt).toLocaleDateString()}</span>
      </div>
    </div>
  );
}
