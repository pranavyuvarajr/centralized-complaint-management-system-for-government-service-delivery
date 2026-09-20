import { useState, useEffect } from 'react';
import { categoryAPI, departmentAPI } from '../services/api';
import ActionMenu from '../components/ActionMenu';
import ReassignModal from '../components/ReassignModal';

export default function AdminCategories() {
  const [categories, setCategories] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ name: '', description: '', departmentId: '', imageRequired: true, locationRequired: true });
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reassignState, setReassignState] = useState(null); // { category, message, options }

  const load = () => {
    setLoading(true);
    setActionError('');
    categoryAPI.getAll()
      .then((res) => setCategories(res.data))
      .catch((err) => setActionError(err.response?.data?.error || 'Failed to load categories. Try refreshing.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    departmentAPI.getAll().then((res) => setDepartments(res.data.filter((d) => d.active))).catch(() => {});
  }, []);

  const resetForm = () => {
    setForm({ name: '', description: '', departmentId: '', imageRequired: true, locationRequired: true });
    setEditing(null);
    setShowForm(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = { ...form, departmentId: form.departmentId ? Number(form.departmentId) : null };
      const { data: saved } = editing
        ? await categoryAPI.update(editing.id, payload)
        : await categoryAPI.create(payload);
      // Update the list immediately from what the server just confirmed, so
      // the change shows up right away even before the background reload below finishes.
      setCategories((prev) => {
        const exists = prev.some((c) => c.id === saved.id);
        return exists ? prev.map((c) => (c.id === saved.id ? saved : c)) : [saved, ...prev];
      });
      resetForm();
      load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save category');
    }
  };

  const startEdit = (c) => {
    setEditing(c);
    setForm({ name: c.name, description: c.description || '', departmentId: c.departmentId || '', imageRequired: c.imageRequired, locationRequired: c.locationRequired !== false });
    setShowForm(true);
  };

  const toggle = async (c) => {
    const label = c.active ? 'deactivate' : 'activate';
    setActionError('');
    try {
      await categoryAPI.toggle(c.id);
      load();
    } catch (err) {
      setActionError(err.response?.data?.error || `Failed to ${label} "${c.name}".`);
    }
  };

  const remove = async (c, reassignTo) => {
    setActionError('');
    if (!reassignTo) {
      const confirmed = window.confirm(
        `Permanently remove "${c.name}"?\n\n` +
        `This cannot be undone. If complaints already use this category, you'll be asked ` +
        `to pick another category to move them to first.`
      );
      if (!confirmed) return;
    }
    try {
      await categoryAPI.remove(c.id, reassignTo);
      setReassignState(null);
      load();
    } catch (err) {
      const data = err.response?.data;
      if (data?.requiresReassignment) {
        setReassignState({ category: c, message: data.error, options: data.options || [] });
      } else {
        setReassignState(null);
        setActionError(data?.error || `Failed to remove "${c.name}".`);
      }
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Complaint Categories</h1>
          <p className="subtitle">Categories shown to citizens when submitting a complaint.</p>
        </div>
        <button className="btn btn--accent" onClick={() => { resetForm(); setShowForm(!showForm); }}>
          {showForm ? 'Cancel' : '➕ Add Category'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {error && <div className="alert alert--error">{error}</div>}
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Name</label>
              <input className="form-control" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Description</label>
              <input className="form-control" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <div className="form-group">
              <label>Auto-route to department</label>
              <select className="form-control" value={form.departmentId}
                onChange={(e) => setForm({ ...form, departmentId: e.target.value })}>
                <option value="">Don't auto-route (admin assigns manually)</option>
                {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
              <span className="hint">
                Complaints in this category are auto-assigned to the least-busy active official in this
                department on submission. You can still reassign any complaint manually at any time.
              </span>
            </div>
            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <input type="checkbox" checked={form.imageRequired}
                  onChange={(e) => setForm({ ...form, imageRequired: e.target.checked })} />
                Photo required when citizens submit a complaint in this category
              </label>
              <span className="hint">
                Turn this off for departments that don't need photo evidence — citizens can still add one
                optionally.
              </span>
            </div>
            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <input type="checkbox" checked={form.locationRequired}
                  onChange={(e) => setForm({ ...form, locationRequired: e.target.checked })} />
                Location required when citizens submit a complaint in this category
              </label>
              <span className="hint">
                Turn this off for issues that aren't tied to a place — citizens can still mark a location
                optionally. The photo and location settings are independent.
              </span>
            </div>
            <button type="submit" className="btn btn--primary">{editing ? 'Save Changes' : 'Create Category'}</button>
          </form>
        </div>
      )}

      <div className="card">
        {actionError && <div className="alert alert--error" style={{ marginBottom: 16 }}>{actionError}</div>}
        {loading ? <div className="loading-block">Loading categories…</div> : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>Name</th><th>Description</th><th>Auto-routes to</th><th>Photo</th><th>Location</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id}>
                    <td>{c.name}</td>
                    <td style={{ color: 'var(--c-text-light)' }}>{c.description}</td>
                    <td style={{ color: 'var(--c-text-light)' }}>{c.departmentName || '— (manual)'}</td>
                    <td style={{ color: 'var(--c-text-light)' }}>{c.imageRequired ? 'Required' : 'Optional'}</td>
                    <td style={{ color: 'var(--c-text-light)' }}>{c.locationRequired !== false ? 'Required' : 'Optional'}</td>
                    <td><span className={`badge ${c.active ? 'badge--resolved' : 'badge--rejected'}`}>{c.active ? 'Active' : 'Inactive'}</span></td>
                    <td>
                      <ActionMenu items={[
                        { label: 'Edit', onClick: () => startEdit(c) },
                        { label: c.active ? 'Deactivate' : 'Activate', onClick: () => toggle(c) },
                        { label: 'Remove', danger: true, onClick: () => remove(c) },
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
          title={`Remove "${reassignState.category.name}"`}
          message={reassignState.message}
          options={reassignState.options}
          onCancel={() => setReassignState(null)}
          onConfirm={(targetId) => remove(reassignState.category, targetId)}
        />
      )}
    </div>
  );
}
