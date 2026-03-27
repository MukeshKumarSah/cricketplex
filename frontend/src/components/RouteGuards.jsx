import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function PublicRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) return <div className="loading-screen">Loading...</div>;

  if (user) {
    return user.teamSetupDone ? <Navigate to="/" replace /> : <Navigate to="/team-setup" replace />;
  }

  return children;
}

export function PrivateRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) return <div className="loading-screen">Loading...</div>;

  if (!user) return <Navigate to="/login" replace />;

  return children;
}

export function TeamSetupGuard({ children }) {
  const { user, loading } = useAuth();

  if (loading) return <div className="loading-screen">Loading...</div>;

  if (!user) return <Navigate to="/login" replace />;

  if (user.teamSetupDone) return <Navigate to="/" replace />;

  return children;
}

export function HomeGuard({ children }) {
  const { user, loading } = useAuth();

  if (loading) return <div className="loading-screen">Loading...</div>;

  if (!user) return <Navigate to="/login" replace />;

  if (!user.teamSetupDone) return <Navigate to="/team-setup" replace />;

  return children;
}

export function AdminGuard({ children }) {
  const { user, loading } = useAuth();

  if (loading) return <div className="loading-screen">Loading...</div>;

  if (!user) return <Navigate to="/login" replace />;

  if (user.role !== 'ADMIN') return <Navigate to="/" replace />;

  return children;
}
