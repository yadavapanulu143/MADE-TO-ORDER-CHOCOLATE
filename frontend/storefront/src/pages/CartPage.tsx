import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../cart/CartContext';
import { useAuth } from '../auth/AuthContext';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { money } from '../lib/format';
import { useState } from 'react';

export function CartPage() {
  const { cart, loading, updateLine, removeLine, clear } = useCart();
  const { signedIn, customer } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);

  async function guarded(action: () => Promise<void>) {
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(err);
    }
  }

  if (!signedIn) {
    return (
      <div className="page narrow">
        <EmptyState title="Sign in to see your cart">
          <Link to="/login" className="btn btn-primary">
            Sign in
          </Link>
        </EmptyState>
      </div>
    );
  }

  if (loading && !cart) return <Spinner label="Loading your cart…" />;

  if (!cart || cart.lines.length === 0) {
    return (
      <div className="page narrow">
        <EmptyState title="Your cart is empty">
          <Link to="/products" className="btn btn-primary">
            Browse chocolate
          </Link>
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Your cart</h1>
      {error ? <ErrorNotice error={error} /> : null}

      <table className="table cart-table">
        <thead>
          <tr>
            <th scope="col">Item</th>
            <th scope="col">Unit price</th>
            <th scope="col">Quantity</th>
            <th scope="col">Line total</th>
            <th scope="col">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {cart.lines.map((line) => (
            <tr key={line.variantId}>
              <td data-label="Item">
                <span className="line-name">{line.productName}</span>
                <span className="muted small">{line.variantLabel}</span>
              </td>
              <td data-label="Unit price">{money(line.unitPriceMinorUnits, line.unitPrice)}</td>
              <td data-label="Quantity">
                <div className="stepper">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    aria-label={`Decrease quantity of ${line.productName}`}
                    onClick={() => guarded(() => updateLine(line.variantId, Math.max(1, line.quantity - 1)))}
                    disabled={line.quantity <= 1}
                  >
                    −
                  </button>
                  <span aria-live="polite">{line.quantity}</span>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    aria-label={`Increase quantity of ${line.productName}`}
                    onClick={() => guarded(() => updateLine(line.variantId, line.quantity + 1))}
                  >
                    +
                  </button>
                </div>
              </td>
              <td data-label="Line total">{money(line.lineTotalMinorUnits, line.lineTotal)}</td>
              <td>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => guarded(() => removeLine(line.variantId))}
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="cart-summary">
        <div>
          <p className="muted">{cart.totalItems} item(s)</p>
          <p className="price price-lg">Estimated {money(cart.estimatedTotalMinorUnits, cart.estimatedTotal, cart.currency)}</p>
          <p className="muted small">Delivery charge is confirmed at checkout from your address PIN code.</p>
        </div>
        <div className="cart-actions">
          <button type="button" className="btn btn-secondary" onClick={() => guarded(clear)}>
            Clear cart
          </button>
          {customer?.fullyVerified ? (
            <button type="button" className="btn btn-primary btn-lg" onClick={() => navigate('/checkout')}>
              Checkout
            </button>
          ) : (
            <div className="notice notice-warn">
              <p>Verify your email and mobile number to check out.</p>
              <Link to="/verify" className="btn btn-secondary">
                Verify now
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
