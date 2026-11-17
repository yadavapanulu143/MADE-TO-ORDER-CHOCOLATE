import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

export function Layout() {
  const { admin, logout, can } = useAdminAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="admin-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className="admin-sidebar">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true">
            ◆
          </span>
          <span className="brand-name">Choco admin</span>
        </Link>
        <nav className="admin-nav" aria-label="Dashboard">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <span className="nav-group">Sell</span>
          <NavLink to="/orders">Orders</NavLink>
          {can(Permissions.CUSTOMER_READ) ? <NavLink to="/customers">Customers</NavLink> : null}
          <NavLink to="/payments">Payments</NavLink>
          <NavLink to="/invoices">Invoices</NavLink>
          <span className="nav-group">Make &amp; send</span>
          <NavLink to="/production">Production</NavLink>
          <NavLink to="/inventory">Ingredients</NavLink>
          <NavLink to="/deliveries">Deliveries</NavLink>
          <NavLink to="/serviceable-areas">Delivery areas</NavLink>
          <span className="nav-group">Catalogue</span>
          <NavLink to="/products">Products</NavLink>
          <span className="nav-group">Messages</span>
          <NavLink to="/notifications">Notifications</NavLink>
          <span className="nav-group">Insight</span>
          <NavLink to="/reports">Reports</NavLink>
          <NavLink to="/exports">Exports</NavLink>
          {can('settings:write') ? <NavLink to="/settings">Settings</NavLink> : null}
        </nav>
      </aside>

      <div className="admin-main-wrap">
        <div className="admin-main">
          <div className="admin-topbar">
            <h1 className="sr-only">Choco administration</h1>
            <div className="admin-identity">
              <span className="muted small">Signed in as</span>
              <strong>{admin?.fullName}</strong>
              <span className="badge badge-neutral">{admin?.role}</span>
            </div>
            <button type="button" className="btn btn-ghost" onClick={handleLogout}>
              Sign out
            </button>
          </div>
          <main id="main">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
