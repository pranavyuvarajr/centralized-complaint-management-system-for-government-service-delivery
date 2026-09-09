import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { complaintAPI, categoryAPI, attachmentAPI } from '../services/api';

export default function ComplaintForm() {
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState({ title: '', description: '', category: '', location: '' });
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    categoryAPI.getActive().then((res) => setCategories(res.data)).catch(() => {});
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.description.trim().length < 20) {
      setError('Complaint description must contain at least 20 characters.');
      return;
    }
    setLoading(true);
    try {
      const { data } = await complaintAPI.create(form);
      if (file) {
        try { await attachmentAPI.upload(data.id, file); } catch { /* non-fatal */ }
      }
      navigate(`/citizen/complaint/${data.id}`);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to submit complaint');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Submit a Complaint</h1>
          <p className="subtitle">Tell us what's wrong — we'll route it to the right department.</p>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 640 }}>
        {error && <div className="alert alert--error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Complaint Title</label>
            <input type="text" className="form-control" required maxLength={150}
              placeholder="e.g. Water leakage near main road"
              value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
          </div>

          <div className="form-group">
            <label>Category</label>
            <select className="form-control" required
              value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}>
              <option value="">Select a category</option>
              {categories.map((c) => <option key={c.id} value={c.name}>{c.name}</option>)}
            </select>
          </div>

          <div className="form-group">
            <label>Location</label>
            <input type="text" className="form-control"
              placeholder="e.g. MG Road, near Municipal School"
              value={form.location} onChange={e => setForm({ ...form, location: e.target.value })} />
          </div>

          <div className="form-group">
            <label>Description</label>
            <textarea className="form-control" required rows={5}
              placeholder="Describe the issue in detail — what, where, and since when."
              value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
            <span className="hint">Minimum 20 characters. Currently {form.description.length}.</span>
          </div>

          <div className="form-group">
            <label>Attachment (optional)</label>
            <input type="file" className="form-control" accept=".jpg,.jpeg,.png,.pdf"
              onChange={e => setFile(e.target.files[0])} />
            <span className="hint">JPG, PNG, or PDF — up to 5MB.</span>
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="submit" className="btn btn--primary" disabled={loading}>
              {loading ? 'Submitting…' : 'Submit Complaint'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => navigate(-1)}>Cancel</button>
          </div>
        </form>
      </div>
    </div>
  );
}
