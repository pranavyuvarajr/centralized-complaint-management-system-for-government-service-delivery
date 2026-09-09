import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useNavigate } from 'react-router-dom';
import NotificationBell from './NotificationBell';

export default function Navbar({ onMenuClick }) {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {user && (
          <button className="navbar__menu-toggle" onClick={onMenuClick} aria-label="Menu">☰</button>
        )}
        <div className="navbar__brand">
          <span className="navbar__brand-mark">CMS</span>
          <span className="navbar__brand-text">
            Complaint Management System
          </span>
        </div>
      </div>
      {user ? (
        <div className="navbar__right">
          <button className="theme-toggle" onClick={toggleTheme} aria-label="Toggle dark mode" title="Toggle dark mode">
            {theme === 'dark' ? '☀️' : '🌙'}
          </button>
          <NotificationBell />
          <span className="navbar__user">
            <strong>{user.name}</strong>
            {user.role.charAt(0) + user.role.slice(1).toLowerCase()}
          </span>
          <button className="navbar__btn" onClick={handleLogout}>Logout</button>
        </div>
      ) : (
        <div className="navbar__right">
          <button className="theme-toggle" onClick={toggleTheme} aria-label="Toggle dark mode" title="Toggle dark mode">
            {theme === 'dark' ? '☀️' : '🌙'}
          </button>
        </div>
      )}
    </nav>
  );
}
