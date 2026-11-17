import { useCallback, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { orderApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';

const PAGE_SIZE = 20;

const STATUSES = [
  'PAYMENT_PENDING',
  'PAYMENT_VERIFICATION_PENDING',
  'PAYMENT_FAILED',
  'CONFIRMED',
  'PREPARATION_STARTED',
  'PREPARATION_ON_HOLD',
  'PREPARATION_COMPLETED',
  'PACKAGING',
  'READY_FOR_DISPATCH',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'DELIVERY_ATTEMPT_FAILED',
  'DELIVERY_RESCHEDULED',
  'DELIVERY_RETURNED',
  'CANCELLED',
  'REFUND_UNDER_REVIEW',
];

export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const status = params.get('status') ?? '';

  const load = useCallback(
    () => orderApi.list({ page, size: PAGE_SIZE, search: search || undefined, status: status ? [status] : [] }),
    [page, search, status],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  function applySearch(value: string) {
    setSearch(value);
    setPage(0);
  }

  function applyStatus(value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set('status', value);
    else next.delete('status');
    setParams(next, { replace: true });
    setPage(0);
  }

  return (
    <div className="stack">
      <h2>Orders</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="order-search">Search</label>
          <input
            id="order-search"
            type="search"
            placeholder="Order number or customer"
            defaultValue={search}
            onChange={(event) => applySearch(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="order-status">Status</label>
          <select id="order-status" value={status} onChange={(event) => applyStatus(event.target.value)}>
            <option value="">All statuses</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {value.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading orders…" /> : null}

      {data && data.content.length === 0 && !loading ? (
        <EmptyState title="No orders match">
          <p className="muted">Try a different search or status.</p>
        </EmptyState>
      ) : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Placed</th>
                <th>Items</th>
                <th>Total</th>
                <th>Status</th>
                <th>Payment</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((order) => (
                <tr key={order.id}>
                  <td className="mono">{order.orderNumber}</td>
                  <td>{dateTime(order.createdAt)}</td>
                  <td>{order.totalItems}</td>
                  <td>{money(order.totalMinorUnits, order.total, order.currency)}</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                  <td>
                    <StatusBadge status={order.paymentStatus} />
                  </td>
                  <td>
                    <Link to={`/orders/${order.id}`} className="link">
                      Open
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data.totalElements} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}
