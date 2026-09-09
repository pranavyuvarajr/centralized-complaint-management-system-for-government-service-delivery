import { useState, useEffect } from 'react';
import { departmentAPI } from '../services/api';
import ActionMenu from '../components/ActionMenu';
import ReassignModal from '../components/ReassignModal';

export default function AdminDepartments() {
  const [departments, setDepartments] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ name: '', description: '', contactEmail: '', contactPhone: '' });
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reassignState, setReassignState] = useState(null); // { department, message, options }

  const load = () => {
    setLoading(true);
    departmentAPI.getAll().then((res) => setDepartments(res.data)).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const resetForm = () => {
    setForm({ name: '', description: '', contactEmail: '', contactPhone: '' });
    setEditing(null);
    setShowForm(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (editing) await departmentAPI.update(editing.id, form);
      else await departmentAPI.create(form);
      resetForm();
      load();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to save department');
    }
  };

  const startEdit = (d) => {
    setEditing(d);
    setForm({ name: d.name, description: d.description || '', contactEmail: d.contactEmail || '', contactPhone: d.contactPhone || '' });
    setShowForm(true);
  };

  const toggle = async (d) => {
    const label = d.active ? 'deactivate' : 'activate';
    if (!window.confirm(`${label.charAt(0).toUpperCase() + label.slice(1)} "${d.name}"?`)) return;
    setActionError('');
    try {
      await departmentAPI.toggle(d.id);
      load();
    } catch (err) {
      setActionError(err.response?.data?.error || `Failed to ${label} "${d.name}".`);
    }
  };

  const remove = async (d, reassignTo) => {
    setActionError('');
    if (!reassignTo) {
      const confirmed = window.confirm(
        `Permanently remove "${d.name}"?\n\n` +
        `This cannot be undone. If complaints or officials are still linked to this department, ` +
        `you'll be asked to pick another department to move them to first.`
      );
      if (!confirmed) return;
    }
    try {
      await departmentAPI.remove(d.id, reassignTo);
      setReassignState(null);
      load();
    } catch (err) {
      const data = err.response?.data;
      if (data?.requiresReassignment) {
        setReassignState({ department: d, message: data.error, options: data.options || [] });
      } else {
        setReassignState(null);
        setActionError(data?.error || `Failed to remove "${d.name}".`);
      }
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Departments</h1>
          <p className="subtitle">Departments handle complaints once assigned. Remove safely when a department is no longer needed.</p>
        </div>
        <button className="btn btn--accent" onClick={() => { resetForm(); setShowForm(!showForm); }}>
          {showForm ? 'Cancel' : '➕ Add Department'}
        </button>
      </div>

      {showForm && (
        <div className="card">
          {error && <div className="alert alert--error">{error}</div>}
          <form onSubmit={handleSubmit}>
            <div className="form-row">
              <div className="form-group">
                <label>Name</label>
                <input className="form-control" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Contact Email</label>
                <input type="email" className="form-control" value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Contact Phone</label>
                <input className="form-control" value={form.contactPhone} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} />
              </div>
            </div>
            <div className="form-group">
              <label>Description</label>
              <textarea className="form-control" rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <button type="submit" className="btn btn--primary">{editing ? 'Save Changes' : 'Create Department'}</button>
          </form>
        </div>
      )}

      <div className="card">
        {actionError && <div className="alert alert--error" style={{ marginBottom: 16 }}>{actionError}</div>}
        {loading ? <div className="loading-block">Loading departments…</div> : (
          <div className="table-wrapper">
            <table>
              <thead><tr><th>Name</th><th>Contact</th><th>Status</th><th>Action</th></tr></thead>
              <tbody>
                {departments.map((d) => (
                  <tr key={d.id}>
                    <td>{d.name}<br /><span style={{ fontSize: '0.75rem', color: 'var(--c-text-faint)' }}>{d.description}</span></td>
                    <td>{d.contactEmail}<br />{d.contactPhone}</td>
                    <td><span className={`badge ${d.active ? 'badge--resolved' : 'badge--rejected'}`}>{d.active ? 'Active' : 'Inactive'}</span></td>
                    <td>
                      <ActionMenu items={[
                        { label: 'Edit', onClick: () => startEdit(d) },
                        { label: d.active ? 'Deactivate' : 'Activate', onClick: () => toggle(d) },
                        { label: 'Remove', danger: true, onClick: () => remove(d) },
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
          title={`Remove "${reassignState.department.name}"`}
          message={reassignState.message}
          options={reassignState.options}
          onCancel={() => setReassignState(null)}
          onConfirm={(targetId) => remove(reassignState.department, targetId)}
        />
      )}
    </div>
  );
}
