import { useState, useEffect } from 'react';
import { categoryAPI } from '../services/api';
import ActionMenu from '../components/ActionMenu';
import ReassignModal from '../components/ReassignModal';

export default function AdminCategories() {
  const [categories, setCategories] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ name: '', description: '' });
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reassignState, setReassignState] = useState(null); // { category, message, options }

  const load = () => {
    setLoading(true);
    categoryAPI.getAll().then((res) => setCategories(res.data)).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const resetForm = () => {
    setForm({ name: '', description: '' });
    setEditing(null);
    setShowForm(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (editing) await categoryAPI.update(editing.id, form);
      else await categoryAPI.create(form);
      resetForm();
      load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save category');
    }
  };

  const startEdit = (c) => {
    setEditing(c);
    setForm({ name: c.name, description: c.description || '' });
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
            <button type="submit" className="btn btn--primary">{editing ? 'Save Changes' : 'Create Category'}</button>
          </form>
        </div>
      )}

      <div className="card">
        {actionError && <div className="alert alert--error" style={{ marginBottom: 16 }}>{actionError}</div>}
        {loading ? <div className="loading-block">Loading categories…</div> : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>Name</th><th>Description</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id}>
                    <td>{c.name}</td>
                    <td style={{ color: 'var(--c-text-light)' }}>{c.description}</td>
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
