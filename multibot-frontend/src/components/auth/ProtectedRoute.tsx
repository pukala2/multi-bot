import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

export function ProtectedRoute() {
  const auth = useAuth();

  if (auth.isPending) {
    return (
      <div className="min-h-screen flex items-center justify-center text-text-muted text-[13px]">
        Wczytywanie…
      </div>
    );
  }

  if (!auth.data) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
