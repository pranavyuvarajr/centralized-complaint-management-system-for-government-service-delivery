import { Navigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function getDashboardPath(user) {
  if (user.role === 'ADMIN') return '/admin';
  if (user.role === 'OFFICIAL') return '/official';
  return '/citizen';
}

export default function Home() {
  const { user, loading } = useAuth();

  // Logged-in users land straight on their dashboard — no extra click needed.
  if (!loading && user) {
    return <Navigate to={getDashboardPath(user)} replace />;
  }

  return (
    <div className="landing">
      <div className="landing__hero">
        <h1>Report. Track. Resolve.</h1>
        <p>
          A centralized platform to register complaints about government services
          and follow their progress, from submission to resolution.
        </p>
        <div className="landing__actions">
          <Link to="/login" className="btn btn--accent">Login</Link>
          <Link to="/register" className="btn btn--outline" style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)' }}>
            Register
          </Link>
          {/* <Link to="/track" className="btn btn--ghost" style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)' }}>
            Track a Complaint
          </Link> */}
        </div>
      </div>

      <div className="landing__section">
        <div className="landing__track">
          <span className="landing__track-icon">📋</span>
          <h3>Already submitted a complaint?</h3>
          <p>
            Enter your complaint reference number to see its current status — no account required.
          </p>
          <Link to="/track" className="btn btn--primary">Track Your Complaint</Link>
        </div>
      </div>

      <div className="landing__footer">
        <p>About · Contact · Privacy · Terms</p>
      </div>
    </div>
  );
}
