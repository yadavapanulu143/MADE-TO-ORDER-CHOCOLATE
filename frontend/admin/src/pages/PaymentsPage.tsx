import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { paymentApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';

const PAGE_SIZE = 20;
const STATUSES = [
  'CREATED',
  'PENDING',
  'PAID',
  'FAILED',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
  'DISPUTED',
];

export function PaymentsPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const load = useCallback(
    () => paymentApi.list({ page, size: PAGE_SIZE, search: search || undefined, status: status ? [status] : [] }),
    [page, search, status],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  return (
    <div className="stack">
      <h2>Payments</h2>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="pay-search">Search</label>
          <input id="pay-search" type="search" placeholder="Order or provider id"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }} />
        </div>
        <div className="field">
          <label htmlFor="pay-status">Status</label>
          <select id="pay-status" value={status}
            onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading payments…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No payments match" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr><th>Order</th><th>Amount</th><th>Method</th><th>Status</th><th>Created</th><th /></tr>
            </thead>
            <tbody>
              {data.content.map((payment) => (
                <tr key={payment.id}>
                  <td className="mono">{payment.orderId.slice(0, 8)}</td>
                  <td>{money(payment.amountMinorUnits, payment.amount, payment.currency)}</td>
                  <td>{payment.method ?? '—'}</td>
                  <td><StatusBadge status={payment.status} /></td>
                  <td>{dateTime(payment.createdAt)}</td>
                  <td><Link to={`/payments/${payment.id}`} className="link">Open</Link></td>
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
