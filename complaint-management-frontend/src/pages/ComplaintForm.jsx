import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { complaintAPI, categoryAPI, profileAPI } from '../services/api';
import LocationPicker from '../components/LocationPicker';
import PhonePrompt from '../components/PhonePrompt';

// The unsaved form is kept in sessionStorage so it survives the page being reloaded
// (phones often reload the tab while the file chooser / camera is open). The photo
// itself can't be stored, so it has to be picked again after a restore.
const DRAFT_KEY = 'complaint-form-draft-v1';

function loadDraft() {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function saveDraft(draft) {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch { /* storage full or blocked - the draft is a convenience only */ }
}

function clearDraft() {
  try { sessionStorage.removeItem(DRAFT_KEY); } catch { /* ignore */ }
}

const EMPTY_FORM = { title: '', description: '', category: '' };

const URGENCY_MIN = 20;
const URGENCY_MAX = 1000;

export default function ComplaintForm() {
  const [draft] = useState(loadDraft); // read once, on first render
  const [categories, setCategories] = useState([]);
  const [form, setForm] = useState({ ...EMPTY_FORM, ...(draft?.form || {}) });
  const [photo, setPhoto] = useState(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState(null);

  // Location is independent of the photo. coords is the single source of truth for the
  // pin: { lat, lon }; locationSource records how it was set (search / gps / pin).
  const [coords, setCoords] = useState(draft?.coords || null);
  const [locationSource, setLocationSource] = useState(draft?.locationSource || '');
  const [searchText, setSearchText] = useState(draft?.searchText || '');

  const [restored] = useState(() => !!(draft && (draft.form?.title || draft.form?.description || draft.coords)));
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  // Normal (default) or Urgent. The urgency choice is deliberately not part of the saved draft,
  // so a restored form never silently comes back as urgent.
  const [urgent, setUrgent] = useState(false);
  const [urgencyReason, setUrgencyReason] = useState('');
  const [askPhone, setAskPhone] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    categoryAPI.getActive().then((res) => setCategories(res.data)).catch(() => {});
  }, []);

  // Save the draft whenever something changes (until the complaint is submitted).
  useEffect(() => {
    saveDraft({ form, coords, locationSource, searchText });
  }, [form, coords, locationSource, searchText]);

  // Release the preview's object URL when it changes or the page closes.
  useEffect(() => () => { if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl); }, [photoPreviewUrl]);

  const selectedCategory = categories.find((c) => c.name === form.category);
  // Unknown / not-yet-loaded category defaults to requiring both, matching the server's
  // conservative default for categories it doesn't recognize.
  const imageRequired = selectedCategory ? selectedCategory.imageRequired : true;
  const locationRequired = selectedCategory ? selectedCategory.locationRequired !== false : true;

  const handleLocationChange = (next, source) => {
    setCoords(next);
    setLocationSource(source);
    setError('');
  };

  const handleLocationClear = () => {
    setCoords(null);
    setLocationSource('');
    setSearchText('');
  };

  const handlePhotoChange = (e) => {
    const file = e.target.files[0];
    setError('');
    if (!file) {
      setPhoto(null);
      setPhotoPreviewUrl(null);
      return;
    }
    setPhoto(file);
    setPhotoPreviewUrl(URL.createObjectURL(file));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    await submit();
  };

  const submit = async (phoneJustSaved = false) => {
    setError('');
    if (form.description.trim().length < 20) {
      setError('Complaint description must contain at least 20 characters.');
      return;
    }
    if (imageRequired && !photo) {
      setError('A photo of the issue is required for this category.');
      return;
    }
    if (locationRequired && !coords) {
      setError('A location is required for this category. Search for an address, use your current location, or drop a pin on the map.');
      return;
    }

    if (urgent) {
      const len = urgencyReason.trim().length;
      if (len < URGENCY_MIN || len > URGENCY_MAX) {
        setError(`Please explain why this is urgent (${URGENCY_MIN} to ${URGENCY_MAX} characters).`);
        return;
      }
    }

    setLoading(true);
    try {
      if (urgent && !phoneJustSaved) {
        // The official may call to verify, so urgent needs a phone number on the account.
        // Reuse the existing phone prompt if it is missing.
        const { data: profile } = await profileAPI.get();
        if (!profile.phone || !String(profile.phone).trim()) {
          setAskPhone(true);
          return;
        }
      }
      const { data } = await complaintAPI.create({
        ...form,
        ...(urgent ? { urgent: true, urgencyReason: urgencyReason.trim() } : {}),
        ...(coords ? { latitude: coords.lat, longitude: coords.lon } : {}),
        photo: photo || undefined,
      });
      clearDraft();
      navigate(`/citizen/complaint/${data.id}`);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to submit complaint');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    clearDraft();
    navigate(-1);
  };

  const canSubmit = !loading && form.title && form.category
    && form.description.trim().length >= 20
    && (!imageRequired || photo)
    && (!locationRequired || coords)
    && (!urgent || urgencyReason.trim().length >= URGENCY_MIN);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Submit a Complaint</h1>
          <p className="subtitle">Tell us what's wrong — we'll route it to the right department.</p>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 720 }}>
        {restored && !photo && (
          <div className="alert alert--info">
            We restored the complaint you were working on. If you had chosen a photo, please select it again.
          </div>
        )}
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
            <label>Description</label>
            <textarea className="form-control" required rows={5}
              placeholder="Describe the issue in detail — what, where, and since when."
              value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
            <span className="hint">Minimum 20 characters. Currently {form.description.length}.</span>
          </div>

          <div className="form-group">
            <label>Priority of this complaint</label>
            <div className="urgency-choice" role="radiogroup" aria-label="Complaint type">
              <label className={`urgency-choice__option${!urgent ? ' urgency-choice__option--active' : ''}`}>
                <input type="radio" name="complaint-type" checked={!urgent} onChange={() => setUrgent(false)} />
                <span><strong>Normal</strong><small>Routed to the department as usual.</small></span>
              </label>
              <label className={`urgency-choice__option urgency-choice__option--urgent${urgent ? ' urgency-choice__option--active' : ''}`}>
                <input type="radio" name="complaint-type" checked={urgent} onChange={() => setUrgent(true)} />
                <span><strong>Urgent</strong><small>Needs immediate attention.</small></span>
              </label>
            </div>
          </div>

          {urgent && (
            <div className="form-group">
              <div className="alert alert--info">
                Urgent complaints are sent to the responsible official immediately. The official may call you to
                verify, and may change the complaint to Normal if it does not qualify as urgent or if you cannot be
                reached. Please describe clearly and professionally why this is urgent.
              </div>
              <label>Why is this urgent?</label>
              <textarea className="form-control" rows={4} maxLength={URGENCY_MAX}
                placeholder="e.g. A main water pipe has burst and is flooding the road outside a school, creating a risk of accidents."
                value={urgencyReason} onChange={e => setUrgencyReason(e.target.value)} />
              <span className="hint">
                {URGENCY_MIN}–{URGENCY_MAX} characters. Currently {urgencyReason.trim().length}. The mobile number on your account will be visible to the official so they can call you.
              </span>
            </div>
          )}

          <div className="form-group">
            <label>Photo of the issue {form.category ? (imageRequired ? '(required)' : '(optional)') : ''}</label>
            <input type="file" className="form-control" accept="image/jpeg,image/png"
              required={imageRequired}
              onChange={handlePhotoChange} />
            <span className="hint">
              {form.category
                ? (imageRequired
                  ? 'Choose a JPG or PNG from your gallery, up to 5MB. This category requires photo evidence.'
                  : 'Choose a JPG or PNG from your gallery, up to 5MB. Optional for this category — add one if it helps explain the issue.')
                : 'Choose a JPG or PNG from your gallery, up to 5MB. Select a category to see whether a photo is required.'}
            </span>
          </div>

          {photoPreviewUrl && (
            <div className="form-group">
              <img src={photoPreviewUrl} alt="Selected complaint evidence"
                style={{ maxWidth: '100%', maxHeight: 220, borderRadius: 10, border: '1px solid var(--c-border)' }} />
            </div>
          )}

          <div className="form-group">
            <label>Location {form.category ? (locationRequired ? '(required)' : '(optional)') : ''}</label>
            <span className="hint" style={{ display: 'block', marginBottom: 8 }}>
              {form.category
                ? (locationRequired
                  ? 'Mark exactly where the issue is. This category requires a location.'
                  : 'Mark where the issue is if it helps. Optional for this category — you can skip it.')
                : 'Mark exactly where the issue is. Select a category to see whether a location is required.'}
              {' '}The location is set separately from your photo.
            </span>
            <LocationPicker
              coords={coords}
              source={locationSource}
              onChange={handleLocationChange}
              onClear={locationRequired ? undefined : handleLocationClear}
              initialQuery={searchText}
              onQueryChange={setSearchText}
            />
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
            <button type="submit" className="btn btn--primary" disabled={!canSubmit}>
              {loading ? 'Submitting…' : 'Submit Complaint'}
            </button>
            <button type="button" className="btn btn--ghost" onClick={handleCancel}>Cancel</button>
          </div>
        </form>
      </div>
      {askPhone && (
        <PhonePrompt force
          onSaved={() => { setAskPhone(false); submit(true); }}
          onCancel={() => setAskPhone(false)} />
      )}
    </div>
  );
}
