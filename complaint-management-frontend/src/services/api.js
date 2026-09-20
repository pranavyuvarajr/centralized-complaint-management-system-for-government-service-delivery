import axios from 'axios';

const API_BASE = 'https://152.67.3.122';

const api = axios.create({
  baseURL: API_BASE + '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 errors globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// --- Auth ---
export const authAPI = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
};

// --- Complaints ---
export const complaintAPI = {
  // data: { title, description, category, latitude, longitude, photo? }
  // Location (latitude/longitude) is required or optional depending on the category. photo is only
  // required when the chosen category has imageRequired=true — the server
  // prefers GPS read straight out of the photo, when one is attached and
  // carries a GPS tag, over the map-picked coordinates.
  create: (data) => {
    const form = new FormData();
    form.append('title', data.title);
    form.append('description', data.description);
    form.append('category', data.category);
    if (data.latitude != null) form.append('latitude', data.latitude);
    if (data.longitude != null) form.append('longitude', data.longitude);
    if (data.photo) form.append('photo', data.photo);
    return api.post('/complaints', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  getAll: (params) => api.get('/complaints', { params }),
  getById: (id) => api.get(`/complaints/${id}`),
  track: (complaintNumber) => api.get(`/complaints/track/${complaintNumber}`),
  updateStatus: (id, data) => api.put(`/complaints/${id}/status`, data),
  updatePriority: (id, priority) => api.patch(`/complaints/${id}/priority`, { priority }),
  // The reason is required by the server. With a photo it is sent as multipart, otherwise as JSON.
  reopen: (id, reason, photo) => {
    if (!photo) return api.post(`/complaints/${id}/reopen`, { reason });
    const form = new FormData();
    form.append('reason', reason);
    form.append('photo', photo);
    return api.post(`/complaints/${id}/reopen`, form, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  // Staff: mark a complaint resolved together with the completion photo. The photo is required
  // when the category requires a citizen photo (see complaint.completionPhotoRequired).
  resolve: (id, { remarks, resolutionInfo, photo }) => {
    const form = new FormData();
    if (remarks) form.append('remarks', remarks);
    if (resolutionInfo) form.append('resolutionInfo', resolutionInfo);
    if (photo) form.append('photo', photo);
    return api.post(`/complaints/${id}/resolve`, form, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  close: (id) => api.post(`/complaints/${id}/close`),
  getHistory: (id) => api.get(`/complaints/${id}/history`),
  citizenStats: () => api.get('/complaints/dashboard/citizen'),
  officialStats: () => api.get('/complaints/dashboard/official'),
};

// --- Comments ---
export const commentAPI = {
  getAll: (complaintId) => api.get(`/complaints/${complaintId}/comments`),
  add: (complaintId, message) => api.post(`/complaints/${complaintId}/comments`, { message }),
};

// --- Feedback ---
export const feedbackAPI = {
  get: (complaintId) => api.get(`/complaints/${complaintId}/feedback`),
  submit: (complaintId, data) => api.post(`/complaints/${complaintId}/feedback`, data),
};

// --- Attachments ---
export const attachmentAPI = {
  getAll: (complaintId) => api.get(`/complaints/${complaintId}/attachments`),
  upload: (complaintId, file) => {
    const form = new FormData();
    form.append('file', file);
    return api.post(`/complaints/${complaintId}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  download: (attachmentId) => api.get(`/attachments/${attachmentId}/download`, { responseType: 'blob' }),
};

// --- Categories ---
export const categoryAPI = {
  getActive: () => api.get('/categories'),
  getAll: () => api.get('/categories/all'),
  create: (data) => api.post('/categories', data),
  update: (id, data) => api.put(`/categories/${id}`, data),
  toggle: (id) => api.patch(`/categories/${id}/toggle`),
  remove: (id, reassignTo) => api.delete(`/categories/${id}`, { params: reassignTo ? { reassignTo } : {} }),
};

// --- Departments ---
export const departmentAPI = {
  getAll: () => api.get('/departments'),
  create: (data) => api.post('/departments', data),
  update: (id, data) => api.put(`/departments/${id}`, data),
  toggle: (id) => api.patch(`/departments/${id}/toggle`),
  remove: (id, reassignTo) => api.delete(`/departments/${id}`, { params: reassignTo ? { reassignTo } : {} }),
};

// --- Geocoding (location search for the complaint-form map picker) ---
export const geocodeAPI = {
  // lat/lon (optional) bias results toward where the user is looking. A 200 with [] means
  // "no matches"; a 503 means the geocoding services are unreachable.
  search: (q, { lat, lon, signal } = {}) =>
    api.get('/geocode/search', { params: { q, ...(lat != null && lon != null ? { lat, lon } : {}) }, signal }),
  reverse: (lat, lon, { signal } = {}) => api.get('/geocode/reverse', { params: { lat, lon }, signal }),
};

// --- Notifications ---
export const notificationAPI = {
  getAll: () => api.get('/notifications'),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id) => api.patch(`/notifications/${id}/read`),
  markAllRead: () => api.patch('/notifications/read-all'),
};

// --- Profile ---
export const profileAPI = {
  get: () => api.get('/users/me'),
  update: (data) => api.patch('/users/me', data),
  changePassword: (data) => api.post('/users/me/password', data),
};

// --- Admin ---
export const adminAPI = {
  getUsers: () => api.get('/admin/users'),
  getUsersByRole: (role) => api.get(`/admin/users/role/${role}`),
  createUser: (data) => api.post('/admin/users', data),
  updateUser: (id, data) => api.put(`/admin/users/${id}`, data),
  setUserActive: (id, active) => api.patch(`/admin/users/${id}/active`, { active }),
  deleteUser: (id, reassignTo) => api.delete(`/admin/users/${id}`, { params: reassignTo ? { reassignTo } : {} }),
  resetPassword: (id, newPassword, adminCurrentPassword) =>
    api.patch(`/admin/users/${id}/password`, { newPassword, adminCurrentPassword }),
  assignComplaint: (id, data) => api.patch(`/admin/complaints/${id}/assign`, data),
  // Permanent delete; the admin must confirm with their own password.
  deleteComplaint: (id, adminPassword) => api.delete(`/admin/complaints/${id}`, { data: { adminPassword } }),
  updatePriority: (id, priority) => api.patch(`/admin/complaints/${id}/priority`, { priority }),
  getStats: () => api.get('/admin/stats'),
  getAuditLogs: (params) => api.get('/admin/audit-logs', { params }),
};

export default api;
