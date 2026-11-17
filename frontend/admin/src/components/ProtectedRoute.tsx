import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Spinner } from './Feedback';

/** Gate for the dashboard. Sends an unauthenticated visitor to the admin sign-in
 *  and remembers where they were headed so login can return them there. */
export function ProtectedRoute() {
  const { signedIn, loading } = useAdminAuth();
  const location = useLocation();

  if (loading) return <Spinner label="Checking your session…" />;
  if (!signedIn) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}

/**
 * Gate a single page on a permission. The backend authorises every call anyway, so
 * this only spares an administrator a control that would be refused.
 */
export function RequirePermission({ permission, children }: { permission: string; children: React.ReactNode }) {
  const { can } = useAdminAuth();
  if (!can(permission)) {
    return (
      <div className="notice notice-error" role="alert">
        You do not have permission to view this page.
      </div>
    );
  }
  return <>{children}</>;
}
