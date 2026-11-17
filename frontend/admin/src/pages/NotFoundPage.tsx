import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="empty-state">
      <h2>Page not found</h2>
      <p className="muted">That page does not exist in the dashboard.</p>
      <Link to="/" className="btn btn-primary">
        Back to the dashboard
      </Link>
    </div>
  );
}
