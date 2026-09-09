import axios from 'axios';

const API_BASE = 'https://instead-desktop-departmental-reservations.trycloudflare.com';

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
  create: (data) => api.post('/complaints', data),
  getAll: (params) => api.get('/complaints', { params }),
  getById: (id) => api.get(`/complaints/${id}`),
  track: (complaintNumber) => api.get(`/complaints/track/${complaintNumber}`),
  updateStatus: (id, data) => api.put(`/complaints/${id}/status`, data),
  updatePriority: (id, priority) => api.patch(`/complaints/${id}/priority`, { priority }),
  reopen: (id, reason) => api.post(`/complaints/${id}/reopen`, { reason }),
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
  setUserActive: (id, active) => api.patch(`/admin/users/${id}/active`, { active }),
  deleteUser: (id, reassignTo) => api.delete(`/admin/users/${id}`, { params: reassignTo ? { reassignTo } : {} }),
  resetPassword: (id, newPassword, adminCurrentPassword) =>
    api.patch(`/admin/users/${id}/password`, { newPassword, adminCurrentPassword }),
  assignComplaint: (id, data) => api.patch(`/admin/complaints/${id}/assign`, data),
  updatePriority: (id, priority) => api.patch(`/admin/complaints/${id}/priority`, { priority }),
  getStats: () => api.get('/admin/stats'),
  getAuditLogs: (params) => api.get('/admin/audit-logs', { params }),
};

export default api;
