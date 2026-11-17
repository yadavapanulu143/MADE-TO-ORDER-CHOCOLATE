import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { orderApi } from '../api/endpoints';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { StatusBadge } from '../components/StatusBadge';
import { formatDate, money } from '../lib/format';
import type { OrderSummary } from '../api/types';

const PAGE_SIZE = 10;

export function OrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    setOrders(null);
    orderApi
      .list({ page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return;
        setOrders(result.content);
        setTotal(result.totalElements);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="page">
      <h1>Your orders</h1>
      {error ? <ErrorNotice error={error} /> : null}
      {!orders && !error ? <Spinner label="Loading your orders…" /> : null}

      {orders && orders.length === 0 ? (
        <EmptyState title="You have not ordered yet">
          <Link to="/products" className="btn btn-primary">
            Browse chocolate
          </Link>
        </EmptyState>
      ) : null}

      {orders && orders.length > 0 ? (
        <>
          <ul className="order-list">
            {orders.map((order) => (
              <li key={order.id} className="order-card">
                <Link to={`/orders/${order.id}`}>
                  <div className="order-head">
                    <span className="order-number">{order.orderNumber}</span>
                    <StatusBadge status={order.status} />
                  </div>
                  <dl className="order-meta">
                    <div>
                      <dt>Placed</dt>
                      <dd>{formatDate(order.createdAt)}</dd>
                    </div>
                    <div>
                      <dt>Items</dt>
                      <dd>{order.totalItems}</dd>
                    </div>
                    <div>
                      <dt>Payment</dt>
                      <dd>
                        <StatusBadge status={order.paymentStatus} />
                      </dd>
                    </div>
                    <div>
                      <dt>Total</dt>
                      <dd>{money(order.totalMinorUnits, order.total, order.currency)}</dd>
                    </div>
                  </dl>
                </Link>
              </li>
            ))}
          </ul>

          {totalPages > 1 ? (
            <nav className="pagination" aria-label="Pagination">
              <button
                type="button"
                className="btn btn-secondary"
                disabled={page <= 0}
                onClick={() => setPage((current) => current - 1)}
              >
                Previous
              </button>
              <span className="muted">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
