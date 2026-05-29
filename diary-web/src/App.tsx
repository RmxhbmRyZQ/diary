import { type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import RecoveryPage from './pages/RecoveryPage';
import UnlockPage from './pages/UnlockPage';
import DiaryListPage from './pages/DiaryListPage';
import EditorPage from './pages/EditorPage';
import StatisticsPage from './pages/StatisticsPage';
import SettingsPage from './pages/SettingsPage';
import AdminLoginPage from './pages/Admin/AdminLoginPage';
import AdminLayout from './pages/Admin/AdminLayout';
import AdminDashboardPage from './pages/Admin/AdminDashboardPage';
import AdminUsersPage from './pages/Admin/AdminUsersPage';
import AdminConfigPage from './pages/Admin/AdminConfigPage';

function LoadingSpinner() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-warm-50">
      <p className="text-gray-400">加载中...</p>
    </div>
  );
}

function UnlockRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading, needsUnlock } = useAuth();

  if (isLoading) return <LoadingSpinner />;
  if (isAuthenticated) return <Navigate to="/" replace />;
  if (needsUnlock) return <>{children}</>;

  return <Navigate to="/login" replace />;
}

function GuestRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <LoadingSpinner />;
  if (isAuthenticated) return <Navigate to="/" replace />;

  return <>{children}</>;
}

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading, needsUnlock } = useAuth();

  if (isLoading) return <LoadingSpinner />;
  if (needsUnlock) return <Navigate to="/unlock" replace />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;

  return <>{children}</>;
}

function AppRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/recovery" element={<GuestRoute><RecoveryPage /></GuestRoute>} />
        <Route path="/unlock" element={<UnlockRoute><UnlockPage /></UnlockRoute>} />
        <Route path="/" element={<ProtectedRoute><DiaryListPage /></ProtectedRoute>} />
        <Route path="/editor/new" element={<ProtectedRoute><EditorPage /></ProtectedRoute>} />
        <Route path="/editor/:id" element={<ProtectedRoute><EditorPage /></ProtectedRoute>} />
        <Route path="/statistics" element={<ProtectedRoute><StatisticsPage /></ProtectedRoute>} />
        <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminDashboardPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="config" element={<AdminConfigPage />} />
        </Route>
        <Route path="*" element={<AppRoutes />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
