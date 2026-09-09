import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { notificationAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

function timeAgo(dateStr) {
  const diff = (Date.now() - new Date(dateStr).getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

export default function NotificationBell() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [unread, setUnread] = useState(0);
  const navigate = useNavigate();
  const ref = useRef(null);

  const load = () => {
    notificationAPI.unreadCount().then((res) => setUnread(res.data.count)).catch(() => {});
  };

  useEffect(() => {
    if (!user) return;
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, [user]);

  useEffect(() => {
    const onClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  const toggle = () => {
    if (!open) {
      notificationAPI.getAll().then((res) => setItems(res.data)).catch(() => {});
    }
    setOpen(!open);
  };

  const handleItemClick = (n) => {
    if (!n.read) notificationAPI.markRead(n.id).then(load).catch(() => {});
    setOpen(false);
    if (n.complaintId) {
      const base = user.role === 'ADMIN' ? '/admin' : user.role === 'OFFICIAL' ? '/official' : '/citizen';
      navigate(`${base}/complaint/${n.complaintId}`);
    }
  };

  const markAll = () => {
    notificationAPI.markAllRead().then(() => {
      setItems((prev) => prev.map((n) => ({ ...n, read: true })));
      load();
    }).catch(() => {});
  };

  if (!user) return null;

  return (
    <div className="notif" ref={ref}>
      <button className="notif__btn" onClick={toggle} aria-label="Notifications">
        🔔
        {unread > 0 && <span className="notif__dot">{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className="notif__panel">
          <div className="notif__header">
            <h4>Notifications</h4>
            {items.some((n) => !n.read) && <button onClick={markAll}>Mark all read</button>}
          </div>
          {items.length === 0 ? (
            <div className="notif__empty">No notifications yet.</div>
          ) : (
            items.map((n) => (
              <div key={n.id} className={`notif__item ${!n.read ? 'unread' : ''}`} onClick={() => handleItemClick(n)}>
                <div>{n.message}</div>
                <div className="notif__item-meta">{timeAgo(n.createdAt)}</div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
