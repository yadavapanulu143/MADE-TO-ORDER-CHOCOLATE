import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime } from '../lib/format';

const PAGE_SIZE = 20;

const STATUSES = ['PENDING', 'SENT', 'FAILED', 'DEAD_LETTER', 'SUPPRESSED'];
const TYPES = [
  'ORDER_PLACED',
  'ORDER_CONFIRMED',
  'ORDER_CANCELLED',
  'INVOICE_ISSUED',
  'PAYMENT_SUCCESS',
  'PAYMENT_FAILED',
  'PAYMENT_PENDING',
  'REFUND_REQUESTED',
  'REFUND_PROCESSED',
  'DISPUTE_RAISED',
  'PRODUCTION_STARTED',
  'PRODUCTION_COMPLETED',
  'READY_FOR_DISPATCH',
  'DELIVERY_ASSIGNED',
  'OUT_FOR_DELIVERY',
  'DELIVERY_DELIVERED',
  'DELIVERY_FAILED',
  'LOW_STOCK_ALERT',
];

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [type, setType] = useState('');

  const load = useCallback(
    () =>
      notificationApi.list({
        page,
        size: PAGE_SIZE,
        search: search || undefined,
        status: status ? [status] : [],
        type: type || undefined,
      }),
    [page, search, status, type],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const rows = data?.content ?? [];

  return (
    <div className="stack">
      <h2>Notifications</h2>
      <p className="muted">
        The delivery record for every message the platform has tried to send. A failed message rests
        as failed and can be re-sent; it is never silently retried forever.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="notif-search">Search</label>
          <input
            id="notif-search"
            type="search"
            placeholder="Subject or reference"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="notif-status">Status</label>
          <select id="notif-status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="notif-type">Type</label>
          <select id="notif-type" value={type} onChange={(event) => setType(event.target.value)}>
            <option value="">All types</option>
            {TYPES.map((value) => (
              <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>
            ))}
          </select>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading the notification log…" /> : null}
      {data && rows.length === 0 && !loading ? <EmptyState title="No notifications match" /> : null}

      {rows.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Channel</th>
                <th>Recipient</th>
                <th>Subject</th>
                <th>Status</th>
                <th>Attempts</th>
                <th>Created</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <tr key={item.id}>
                  <td>{item.type.replaceAll('_', ' ')}</td>
                  <td>{item.channel}</td>
                  <td className="mono">{item.recipientMasked}</td>
                  <td>{item.subject}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td>{item.attempts}</td>
                  <td>{dateTime(item.createdAt)}</td>
                  <td><Link to={`/notifications/${item.id}`} className="link">Open</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data?.totalElements ?? 0} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}
