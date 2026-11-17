import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { deliveryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { money } from '../lib/format';

const PAGE_SIZE = 20;

const STATUSES = [
  'PENDING',
  'ASSIGNED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'ATTEMPT_FAILED',
  'RESCHEDULED',
  'CANCELLED',
  'RETURNED',
];

export function DeliveriesPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const load = useCallback(
    () => deliveryApi.list({ page, size: PAGE_SIZE, search: search || undefined, status: status ? [status] : [] }),
    [page, search, status],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  return (
    <div className="stack">
      <h2>Deliveries</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="del-search">Search</label>
          <input
            id="del-search"
            type="search"
            placeholder="Order number or recipient"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="del-status">Status</label>
          <select
            id="del-status"
            value={status}
            onChange={(event) => { setStatus(event.target.value); setPage(0); }}
          >
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
      {loading ? <Spinner label="Loading deliveries…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No deliveries to show" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Recipient</th>
                <th>PIN</th>
                <th>Charge</th>
                <th>Status</th>
                <th>Delivery person</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((delivery) => (
                <tr key={delivery.id}>
                  <td className="mono">{delivery.orderNumber}</td>
                  <td>{delivery.address.recipientName}</td>
                  <td>{delivery.pincode}</td>
                  <td>{money(delivery.deliveryChargeMinorUnits, delivery.deliveryCharge, delivery.currency)}</td>
                  <td><StatusBadge status={delivery.status} /></td>
                  <td>{delivery.deliveryPersonName ?? '—'}</td>
                  <td><Link to={`/deliveries/${delivery.orderId}`} className="link">Open</Link></td>
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
