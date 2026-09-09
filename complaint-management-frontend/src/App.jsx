import { useState } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import ProtectedRoute from './routes/ProtectedRoute';

// Public / shared pages
import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import TrackComplaint from './pages/TrackComplaint';
import Profile from './pages/Profile';

// Citizen
import CitizenDashboard from './pages/CitizenDashboard';
import ComplaintForm from './pages/ComplaintForm';
import ComplaintDetails from './pages/ComplaintDetails';

// Official
import OfficialDashboard from './pages/OfficialDashboard';

// Admin
import AdminDashboard from './pages/AdminDashboard';
import AdminComplaints from './pages/AdminComplaints';
import AdminUsers from './pages/AdminUsers';
import AdminDepartments from './pages/AdminDepartments';
import AdminCategories from './pages/AdminCategories';
import AdminAuditLogs from './pages/AdminAuditLogs';

function DashboardLayout({ children }) {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <>
      <Navbar onMenuClick={() => setMenuOpen((v) => !v)} />
      <div className="app-layout">
        <Sidebar open={menuOpen} onNavigate={() => setMenuOpen(false)} />
        <main className="main-content">{children}</main>
      </div>
    </>
  );
}

export default function App() {
  const { user } = useAuth();

  return (
    <Routes>
      {/* Public */}
      <Route path="/" element={<Home />} />
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <Login />} />
      <Route path="/register" element={user ? <Navigate to="/" replace /> : <Register />} />
      <Route path="/track" element={<TrackComplaint />} />

      {/* Citizen */}
      <Route path="/citizen" element={
        <ProtectedRoute roles={['CITIZEN']}>
          <DashboardLayout><CitizenDashboard /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/citizen/new" element={
        <ProtectedRoute roles={['CITIZEN']}>
          <DashboardLayout><ComplaintForm /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/citizen/complaint/:id" element={
        <ProtectedRoute roles={['CITIZEN']}>
          <DashboardLayout><ComplaintDetails /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/citizen/profile" element={
        <ProtectedRoute roles={['CITIZEN']}>
          <DashboardLayout><Profile /></DashboardLayout>
        </ProtectedRoute>
      } />

      {/* Official */}
      <Route path="/official" element={
        <ProtectedRoute roles={['OFFICIAL']}>
          <DashboardLayout><OfficialDashboard /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/official/complaint/:id" element={
        <ProtectedRoute roles={['OFFICIAL']}>
          <DashboardLayout><ComplaintDetails /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/official/profile" element={
        <ProtectedRoute roles={['OFFICIAL']}>
          <DashboardLayout><Profile /></DashboardLayout>
        </ProtectedRoute>
      } />

      {/* Admin */}
      <Route path="/admin" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminDashboard /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/complaints" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminComplaints /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/complaint/:id" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><ComplaintDetails /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/users" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminUsers /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/departments" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminDepartments /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/categories" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminCategories /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/audit-logs" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><AdminAuditLogs /></DashboardLayout>
        </ProtectedRoute>
      } />
      <Route path="/admin/profile" element={
        <ProtectedRoute roles={['ADMIN']}>
          <DashboardLayout><Profile /></DashboardLayout>
        </ProtectedRoute>
      } />

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
