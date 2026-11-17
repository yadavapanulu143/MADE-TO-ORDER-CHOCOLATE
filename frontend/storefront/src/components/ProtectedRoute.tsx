import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Spinner } from './Feedback';

/** Gate for pages that need a signed-in customer. Sends the visitor to sign in and
 *  remembers where they were headed so login can return them there. */
export function ProtectedRoute() {
  const { signedIn, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Spinner label="Checking your session…" />;
  if (!signedIn) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}
