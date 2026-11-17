import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useSettings } from '../lib/useSettings';

export function Layout() {
  const { signedIn, customer, logout } = useAuth();
  const { itemCount } = useCart();
  const settings = useSettings();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/');
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true">
              ◆
            </span>
            <span className="brand-name">{settings.sellerName || 'Choco'}</span>
          </Link>

          <nav className="main-nav" aria-label="Primary">
            <NavLink to="/products">Shop</NavLink>
            {signedIn ? <NavLink to="/orders">Orders</NavLink> : null}
            {signedIn ? <NavLink to="/invoices">Invoices</NavLink> : null}
            {signedIn ? <NavLink to="/profile">Profile</NavLink> : null}
          </nav>

          <div className="header-actions">
            <Link to="/cart" className="cart-link" aria-label={`Cart, ${itemCount} items`}>
              Cart
              {itemCount > 0 ? <span className="cart-count">{itemCount}</span> : null}
            </Link>
            {signedIn ? (
              <>
                <span className="signed-in-as" title={customer?.email ?? ''}>
                  {customer?.fullName}
                </span>
                <button type="button" className="btn btn-ghost" onClick={handleLogout}>
                  Sign out
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn btn-ghost">
                  Sign in
                </Link>
                <Link to="/register" className="btn btn-primary">
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main" className="container main-content">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="container footer-inner">
          <div>
            <p className="footer-brand">{settings.sellerName || 'Choco'}</p>
            {settings.contactEmail ? <p className="muted">{settings.contactEmail}</p> : null}
            {settings.contactPhone ? <p className="muted">{settings.contactPhone}</p> : null}
          </div>
          <nav className="footer-nav" aria-label="Policies">
            <Link to="/policies">Shipping, refunds &amp; terms</Link>
            <Link to="/serviceability">Delivery areas</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
