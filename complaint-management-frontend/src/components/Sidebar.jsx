import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Sidebar({ open, onNavigate }) {
  const { user } = useAuth();
  if (!user) return null;

  const linkClass = ({ isActive }) => `sidebar__link${isActive ? ' active' : ''}`;

  return (
    <aside className={`sidebar ${open ? 'open' : ''}`} onClick={onNavigate}>
      {user.role === 'CITIZEN' && (
        <>
          <div className="sidebar__section">Dashboard</div>
          <NavLink to="/citizen" end className={linkClass}>📋 My Complaints</NavLink>
          <NavLink to="/citizen/new" className={linkClass}>➕ New Complaint</NavLink>
          <div className="sidebar__section">Account</div>
          <NavLink to="/citizen/profile" className={linkClass}>👤 Profile</NavLink>
          <NavLink to="/track" className={linkClass}>🔎 Track a Complaint</NavLink>
        </>
      )}

      {user.role === 'OFFICIAL' && (
        <>
          <div className="sidebar__section">Department</div>
          <NavLink to="/official" end className={linkClass}>📋 Assigned Complaints</NavLink>
          <div className="sidebar__section">Account</div>
          <NavLink to="/official/profile" className={linkClass}>👤 Profile</NavLink>
        </>
      )}

      {user.role === 'ADMIN' && (
        <>
          <div className="sidebar__section">Overview</div>
          <NavLink to="/admin" end className={linkClass}>📊 Dashboard</NavLink>
          <div className="sidebar__section">Management</div>
          <NavLink to="/admin/complaints" className={linkClass}>📋 All Complaints</NavLink>
          <NavLink to="/admin/users" className={linkClass}>👥 Users</NavLink>
          <NavLink to="/admin/departments" className={linkClass}>🏢 Departments</NavLink>
          <NavLink to="/admin/categories" className={linkClass}>🏷️ Categories</NavLink>
          <NavLink to="/admin/audit-logs" className={linkClass}>🧾 Audit Logs</NavLink>
          <div className="sidebar__section">Account</div>
          <NavLink to="/admin/profile" className={linkClass}>👤 Profile</NavLink>
        </>
      )}
    </aside>
  );
}
