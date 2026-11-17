import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="page narrow">
      <h1>Page not found</h1>
      <p className="muted">The page you were looking for does not exist.</p>
      <Link to="/" className="btn btn-primary">
        Back to the shop
      </Link>
    </div>
  );
}
