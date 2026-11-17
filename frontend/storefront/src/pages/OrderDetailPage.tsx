import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { fulfilmentApi, invoiceApi, orderApi, paymentApi } from '../api/endpoints';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, money } from '../lib/format';
import { useAuth } from '../auth/AuthContext';
import { useSettings } from '../lib/useSettings';
import { loadRazorpay } from '../lib/razorpay';
import type { Delivery, Invoice, Order, Payment, ProductionJob } from '../api/types';

export function OrderDetailPage() {
  const { orderId = '' } = useParams();
  const location = useLocation();
  const { customer } = useAuth();
  const settings = useSettings();

  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [jobs, setJobs] = useState<ProductionJob[]>([]);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);

  const [paying, setPaying] = useState(false);
  const [payMessage, setPayMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const [reason, setReason] = useState('');
  const [showCancel, setShowCancel] = useState(false);
  const [showRefund, setShowRefund] = useState(false);

  const justPlaced = Boolean((location.state as { justPlaced?: boolean } | null)?.justPlaced);

  const load = useCallback(async () => {
    const value = await orderApi.get(orderId);
    setOrder(value);
    // Preparation and delivery progress live in the fulfilment service and may not
    // exist yet; a 404 there is simply "nothing recorded yet", not an error.
    fulfilmentApi.preparation(orderId).then(setJobs).catch(() => setJobs([]));
    fulfilmentApi.delivery(orderId).then(setDelivery).catch(() => setDelivery(null));
    paymentApi.forOrder(orderId).then(setPayment).catch(() => setPayment(null));
    invoiceApi.byOrder(orderId).then(setInvoice).catch(() => setInvoice(null));
  }, [orderId]);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    load().catch((err) => {
      if (!cancelled) setError(err);
    });
    return () => {
      cancelled = true;
    };
  }, [load]);

  async function startPayment() {
    if (!order) return;
    setActionError(null);
    setPayMessage(null);
    setPaying(true);
    try {
      const initiation = await paymentApi.initiate(order.id, crypto.randomUUID());
      if (!initiation.publicKey) {
        setPayMessage('Online payment is not configured on this deployment yet.');
        return;
      }
      const Razorpay = await loadRazorpay();
      const checkout = new Razorpay({
        key: initiation.publicKey,
        amount: initiation.amountMinorUnits,
        currency: initiation.currency,
        name: settings.sellerName || 'Choco',
        description: `Order ${order.orderNumber}`,
        order_id: initiation.providerOrderId,
        prefill: {
          name: customer?.fullName,
          email: customer?.email,
          contact: customer?.mobile,
        },
        theme: { color: '#6b3f2a' },
        handler: (response) => {
          // The browser response is not proof of payment: send it to the server,
          // which re-verifies the signature and re-fetches the provider's state.
          paymentApi
            .verify(order.id, {
              providerOrderId: response.razorpay_order_id,
              providerPaymentId: response.razorpay_payment_id,
              signature: response.razorpay_signature,
            })
            .then(() => {
              setPayMessage('Payment verified. Thank you!');
              return load();
            })
            .catch((err) => setActionError(err));
        },
        modal: {
          ondismiss: () => setPayMessage('Payment window closed before it was completed.'),
        },
      });
      checkout.open();
    } catch (err) {
      setActionError(err);
    } finally {
      setPaying(false);
    }
  }

  async function submitCancel() {
    if (!order) return;
    setActionError(null);
    try {
      await orderApi.cancel(order.id, reason || 'Cancelled by the customer');
      setShowCancel(false);
      setReason('');
      await load();
    } catch (err) {
      setActionError(err);
    }
  }

  async function submitRefund() {
    if (!order) return;
    setActionError(null);
    try {
      await orderApi.requestRefund(order.id, reason || 'Refund requested by the customer');
      setShowRefund(false);
      setReason('');
      await load();
    } catch (err) {
      setActionError(err);
    }
  }

  if (error) {
    return (
      <div className="page">
        <ErrorNotice error={error} />
        <Link to="/orders" className="btn btn-secondary">
          Back to your orders
        </Link>
      </div>
    );
  }

  if (!order) return <Spinner label="Loading your order…" />;

  const paymentPending = order.paymentStatus === 'PENDING';
  const paid = order.paymentStatus === 'PAID';

  return (
    <div className="page order-detail">
      {justPlaced ? (
        <div className="notice notice-success" role="status">
          <p>Your order is placed. Complete the payment to start preparation.</p>
        </div>
      ) : null}

      <header className="page-head">
        <div>
          <h1>Order {order.orderNumber}</h1>
          <p className="muted">Placed {formatDateTime(order.createdAt)}</p>
        </div>
        <StatusBadge status={order.status} />
      </header>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      {payMessage ? (
        <div className="notice notice-info" role="status">
          {payMessage}
        </div>
      ) : null}

      {paymentPending ? (
        <div className="notice notice-warn pay-cta">
          <p>This order is awaiting payment. Preparation has not started.</p>
          <button type="button" className="btn btn-primary btn-lg" onClick={startPayment} disabled={paying}>
            {paying ? 'Starting…' : 'Pay now'}
          </button>
        </div>
      ) : null}

      {paid ? (
        <div className="notice notice-success" role="status">
          <p>Payment received. Your chocolate is being made to order.</p>
        </div>
      ) : null}

      <div className="order-detail-grid">
        <section className="order-items">
          <h2>Items</h2>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Item</th>
                <th scope="col">Unit price</th>
                <th scope="col">Qty</th>
                <th scope="col">Line total</th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((line) => (
                <tr key={line.variantId}>
                  <td data-label="Item">
                    <span className="line-name">{line.productName}</span>
                    <span className="muted small">{line.variantLabel}</span>
                  </td>
                  <td data-label="Unit price">{money(line.unitPriceMinorUnits, line.unitPrice)}</td>
                  <td data-label="Qty">{line.quantity}</td>
                  <td data-label="Line total">{money(line.lineTotalMinorUnits, line.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <dl className="totals">
            <div>
              <dt>Items</dt>
              <dd>{money(order.itemsTotalMinorUnits, order.itemsTotal, order.currency)}</dd>
            </div>
            <div>
              <dt>Delivery</dt>
              <dd>{money(order.deliveryChargeMinorUnits, order.deliveryCharge, order.currency)}</dd>
            </div>
            <div className="grand-total">
              <dt>Total</dt>
              <dd>{money(order.totalMinorUnits, order.total, order.currency)}</dd>
            </div>
          </dl>

          <div className="order-actions">
            {order.cancellable ? (
              <button type="button" className="btn btn-secondary" onClick={() => setShowCancel(true)}>
                Cancel order
              </button>
            ) : null}
            {paid ? (
              <button type="button" className="btn btn-secondary" onClick={() => setShowRefund(true)}>
                Request a refund
              </button>
            ) : null}
            {invoice ? (
              <Link to={`/invoices/${invoice.id}`} className="btn btn-secondary">
                View invoice {invoice.invoiceNumber}
              </Link>
            ) : null}
          </div>

          {showCancel ? (
            <div className="inline-action">
              <label>
                Reason for cancelling
                <textarea
                  rows={2}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
              </label>
              <div className="form-actions">
                <button type="button" className="btn btn-danger" onClick={submitCancel}>
                  Confirm cancellation
                </button>
                <button type="button" className="btn btn-ghost" onClick={() => setShowCancel(false)}>
                  Keep order
                </button>
              </div>
            </div>
          ) : null}

          {showRefund ? (
            <div className="inline-action">
              <label>
                Why do you need a refund?
                <textarea
                  rows={2}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
              </label>
              <div className="form-actions">
                <button type="button" className="btn btn-primary" onClick={submitRefund}>
                  Submit refund request
                </button>
                <button type="button" className="btn btn-ghost" onClick={() => setShowRefund(false)}>
                  Never mind
                </button>
              </div>
            </div>
          ) : null}
        </section>

        <aside className="order-aside">
          <section className="panel">
            <h2>Delivery address</h2>
            <address>
              <strong>{order.deliveryAddress.recipientName}</strong>
              <span className="block">{order.deliveryAddress.formatted}</span>
              <span className="block">{order.deliveryAddress.mobile}</span>
            </address>
            {order.deliveryAddress.deliveryInstructions ? (
              <p className="muted small">Instructions: {order.deliveryAddress.deliveryInstructions}</p>
            ) : null}
          </section>

          <section className="panel">
            <h2>Payment</h2>
            <p>
              <StatusBadge status={order.paymentStatus} />
            </p>
            {payment?.method ? <p className="muted small">Method: {payment.method}</p> : null}
            {payment?.failureReason ? <p className="muted small">Reason: {payment.failureReason}</p> : null}
            {payment?.refundedAmountMinorUnits ? (
              <p className="muted small">
                Refunded: {money(payment.refundedAmountMinorUnits, payment.refundedAmount, payment.currency)}
              </p>
            ) : null}
          </section>

          <section className="panel">
            <h2>Preparation</h2>
            {jobs.length === 0 ? (
              <p className="muted">
                {paid ? 'We will start as soon as the kitchen picks this up.' : 'Starts after payment.'}
              </p>
            ) : (
              <ul className="progress-list">
                {jobs.map((job) => (
                  <li key={job.id}>
                    <span>
                      {job.variantLabel} <span className="muted small">× {job.quantity}</span>
                    </span>
                    <StatusBadge status={job.status} />
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2>Delivery</h2>
            {delivery ? (
              <>
                <p>
                  <StatusBadge status={delivery.status} />
                </p>
                {delivery.deliveryPersonName ? (
                  <p className="muted small">Handled by {delivery.deliveryPersonName}</p>
                ) : null}
                {delivery.dispatchedAt ? (
                  <p className="muted small">Dispatched {formatDateTime(delivery.dispatchedAt)}</p>
                ) : null}
                {delivery.deliveredAt ? (
                  <p className="muted small">Delivered {formatDateTime(delivery.deliveredAt)}</p>
                ) : null}
                {delivery.attemptCount > 0 ? (
                  <p className="muted small">Delivery attempts: {delivery.attemptCount}</p>
                ) : null}
              </>
            ) : (
              <p className="muted">We will record delivery progress here once it is dispatched.</p>
            )}
          </section>

          <section className="panel">
            <h2>Status history</h2>
            <ol className="timeline">
              {order.statusHistory.map((change, index) => (
                <li key={`${change.toStatus}-${index}`}>
                  <span className="timeline-status">{change.toStatus}</span>
                  <span className="muted small">{formatDateTime(change.occurredAt)}</span>
                  {change.reason ? <span className="muted small">{change.reason}</span> : null}
                </li>
              ))}
            </ol>
          </section>
        </aside>
      </div>
    </div>
  );
}
