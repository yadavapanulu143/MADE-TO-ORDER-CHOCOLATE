import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { addressApi, checkoutApi } from '../api/endpoints';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { AddressForm } from '../components/AddressForm';
import { money } from '../lib/format';
import { useCart } from '../cart/CartContext';
import type { Address, AddressInput, CheckoutQuote } from '../api/types';

export function CheckoutPage() {
  const navigate = useNavigate();
  const { cart, refresh } = useCart();
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [quote, setQuote] = useState<CheckoutQuote | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [placing, setPlacing] = useState(false);
  const [addingAddress, setAddingAddress] = useState(false);

  async function loadAddresses() {
    const list = await addressApi.list();
    setAddresses(list);
    if (list.length > 0) {
      setSelectedId((current) => current ?? (list.find((a) => a.isDefault) ?? list[0]!).id);
    }
  }

  useEffect(() => {
    let cancelled = false;
    addressApi
      .list()
      .then((list) => {
        if (cancelled) return;
        setAddresses(list);
        const preferred = list.find((a) => a.isDefault) ?? list[0];
        if (preferred) setSelectedId(preferred.id);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Re-quote whenever the chosen address changes, so the delivery charge shown is
  // the one the backend would charge for that PIN code.
  useEffect(() => {
    if (!selectedId) {
      setQuote(null);
      return;
    }
    let cancelled = false;
    setQuote(null);
    checkoutApi
      .quote(selectedId)
      .then((result) => {
        if (!cancelled) setQuote(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  async function saveAddress(input: AddressInput) {
    const created = await addressApi.create(input);
    await loadAddresses();
    setSelectedId(created.id);
    setAddingAddress(false);
  }

  async function placeOrder() {
    if (!selectedId) return;
    setError(null);
    setPlacing(true);
    try {
      // One key per checkout attempt: a double-tap or a retried request returns
      // the first order rather than creating a second.
      const key = crypto.randomUUID();
      const order = await checkoutApi.place(selectedId, key);
      await refresh();
      navigate(`/orders/${order.id}`, { state: { justPlaced: true } });
    } catch (err) {
      setError(err);
    } finally {
      setPlacing(false);
    }
  }

  if (!cart || cart.lines.length === 0) {
    return (
      <div className="page narrow">
        <EmptyState title="Nothing to check out">
          <Link to="/products" className="btn btn-primary">
            Browse chocolate
          </Link>
        </EmptyState>
      </div>
    );
  }

  if (!addresses) return <Spinner label="Loading your addresses…" />;

  return (
    <div className="page">
      <h1>Checkout</h1>
      {error ? <ErrorNotice error={error} /> : null}

      <div className="checkout-grid">
        <section className="checkout-main">
          <h2>Delivery address</h2>
          {addresses.length === 0 && !addingAddress ? (
            <p className="muted">You have no saved addresses yet.</p>
          ) : null}

          <ul className="address-list">
            {addresses.map((address) => (
              <li key={address.id} className={selectedId === address.id ? 'selected' : ''}>
                <label>
                  <input
                    type="radio"
                    name="address"
                    checked={selectedId === address.id}
                    onChange={() => setSelectedId(address.id)}
                  />
                  <span>
                    <strong>{address.recipientName}</strong>
                    {address.isDefault ? <span className="badge badge-info">Default</span> : null}
                    <span className="muted block">
                      {address.line1}
                      {address.line2 ? `, ${address.line2}` : ''}, {address.city}, {address.state} —{' '}
                      {address.pincode}
                    </span>
                    <span className="muted block">{address.mobile}</span>
                  </span>
                </label>
              </li>
            ))}
          </ul>

          {addingAddress ? (
            <AddressForm onSave={saveAddress} onCancel={() => setAddingAddress(false)} />
          ) : (
            <button type="button" className="btn btn-secondary" onClick={() => setAddingAddress(true)}>
              Add a new address
            </button>
          )}
        </section>

        <aside className="checkout-summary" aria-label="Order summary">
          <h2>Order summary</h2>
          {!quote ? (
            <Spinner label="Pricing your cart…" />
          ) : (
            <>
              <ul className="summary-lines">
                {quote.lines.map((line) => (
                  <li key={line.variantId}>
                    <span>
                      {line.productName} <span className="muted small">× {line.quantity}</span>
                    </span>
                    <span>{money(line.lineTotalMinorUnits, line.lineTotal)}</span>
                  </li>
                ))}
              </ul>
              <dl className="totals">
                <div>
                  <dt>Items</dt>
                  <dd>{money(quote.itemsTotalMinorUnits, quote.itemsTotal, quote.currency)}</dd>
                </div>
                <div>
                  <dt>Delivery</dt>
                  <dd>
                    {quote.serviceable
                      ? money(quote.deliveryChargeMinorUnits, quote.deliveryCharge, quote.currency)
                      : '—'}
                  </dd>
                </div>
                <div className="grand-total">
                  <dt>Total</dt>
                  <dd>{money(quote.totalMinorUnits, quote.total, quote.currency)}</dd>
                </div>
              </dl>

              {!quote.serviceable ? (
                <div className="notice notice-warn" role="alert">
                  <p>{quote.serviceabilityMessage}</p>
                  <Link to="/serviceability" className="btn btn-secondary">
                    Check another PIN code
                  </Link>
                </div>
              ) : (
                <button
                  type="button"
                  className="btn btn-primary btn-lg block"
                  onClick={placeOrder}
                  disabled={placing}
                >
                  {placing ? 'Placing order…' : 'Place order and pay'}
                </button>
              )}
              <p className="muted small">
                You will pay online next. Preparation begins only after your payment is verified.
              </p>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
