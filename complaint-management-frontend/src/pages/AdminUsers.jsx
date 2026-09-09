import { useState, useEffect } from 'react';
import { adminAPI, departmentAPI } from '../services/api';
import ActionMenu from '../components/ActionMenu';
import ReassignModal from '../components/ReassignModal';
import SetPasswordModal from '../components/SetPasswordModal';

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [filter, setFilter] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '', role: 'OFFICIAL', departmentId: '' });
  const [error, setError] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reassignState, setReassignState] = useState(null); // { user, message, options }
  const [passwordUser, setPasswordUser] = useState(null);

  const load = () => {
    setLoading(true);
    adminAPI.getUsers().then((res) => setUsers(res.data)).finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    departmentAPI.getAll().then((res) => setDepartments(res.data)).catch(() => {});
  }, []);

  const filtered = filter ? users.filter((u) => u.role === filter) : users;

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await adminAPI.createUser({ ...form, departmentId: form.departmentId ? Number(form.departmentId) : null });
      setShowForm(false);
      setForm({ name: '', email: '', phone: '', password: '', role: 'OFFICIAL', departmentId: '' });
      load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create user');
    }
  };

  const toggleActive = async (user) => {
    const label = user.active ? 'deactivate' : 'activate';
    if (!window.confirm(`Are you sure you want to ${label} ${user.name}?`)) return;
    await adminAPI.setUserActive(user.id, !user.active);
    load();
  };

  const deleteUser = async (user, reassignTo) => {
    setDeleteError('');
    if (!reassignTo) {
      const confirmed = window.confirm(
        `Permanently delete ${user.name} (${user.email})?\n\n` +
        `This cannot be undone. Their account and login access will be removed entirely — ` +
        `this is different from deactivating, which keeps the record but blocks access.\n\n` +
        `If this official has complaints assigned to them, you'll be asked to pick another ` +
        `official in the same department to hand them to first.`
      );
      if (!confirmed) return;
    }
    try {
      await adminAPI.deleteUser(user.id, reassignTo);
      setReassignState(null);
      load();
    } catch (err) {
      const data = err.response?.data;
      if (data?.requiresReassignment) {
        setReassignState({ user, message: data.error, options: data.options || [] });
      } else {
        setReassignState(null);
        setDeleteError(data?.error || `Failed to delete ${user.name}.`);
      }
    }
  };

  const handleSetPassword = async (newPassword, adminCurrentPassword) => {
    await adminAPI.resetPassword(passwordUser.id, newPassword, adminCurrentPassword);
    setPasswordUser(null);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Users</h1>
          <p className="subtitle">Manage citizens, officials, and administrators.</p>
        </div>
        <button className="btn btn--accent" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Cancel' : '➕ Add Official / Admin'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {error && <div className="alert alert--error">{error}</div>}
          <form onSubmit={handleCreate}>
            <div className="form-row">
              <div className="form-group">
                <label>Name</label>
                <input className="form-control" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Email</label>
                <input type="email" className="form-control" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Phone</label>
                <input className="form-control" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Temporary Password</label>
                <input type="password" className="form-control" required minLength={6} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Role</label>
                <select className="form-control" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                  <option value="OFFICIAL">Official</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
              {form.role === 'OFFICIAL' && (
                <div className="form-group">
                  <label>Department</label>
                  <select className="form-control" value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}>
                    <option value="">Select department</option>
                    {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
                  </select>
                </div>
              )}
            </div>
            <button type="submit" className="btn btn--primary">Create User</button>
          </form>
        </div>
      )}

      <div className="chip-row" style={{ marginBottom: 16 }}>
        {['', 'CITIZEN', 'OFFICIAL', 'ADMIN'].map((r) => (
          <button key={r} className={`chip ${filter === r ? 'active' : ''}`} onClick={() => setFilter(r)}>
            {r || 'All'}
          </button>
        ))}
      </div>

      <div className="card">
        {deleteError && <div className="alert alert--error" style={{ marginBottom: 16 }}>{deleteError}</div>}
        {loading ? <div className="loading-block">Loading users…</div> : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.id}>
                    <td>{u.name}</td>
                    <td>{u.email}</td>
                    <td>{u.role}</td>
                    <td>{u.departmentName || '—'}</td>
                    <td><span className={`badge ${u.active ? 'badge--resolved' : 'badge--rejected'}`}>{u.active ? 'Active' : 'Inactive'}</span></td>
                    <td>
                      <ActionMenu items={[
                        { label: u.active ? 'Deactivate' : 'Activate', onClick: () => toggleActive(u) },
                        { label: 'Change Password', onClick: () => setPasswordUser(u) },
                        { label: 'Delete', danger: true, onClick: () => deleteUser(u) },
                      ]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {reassignState && (
        <ReassignModal
          title={`Reassign ${reassignState.user.name}'s complaints`}
          message={reassignState.message}
          options={reassignState.options}
          onCancel={() => setReassignState(null)}
          onConfirm={(targetId) => deleteUser(reassignState.user, targetId)}
        />
      )}

      {passwordUser && (
        <SetPasswordModal
          user={passwordUser}
          onConfirm={handleSetPassword}
          onCancel={() => setPasswordUser(null)}
        />
      )}
    </div>
  );
}
