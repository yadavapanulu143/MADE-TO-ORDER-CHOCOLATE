import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { productionApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime } from '../lib/format';

const PAGE_SIZE = 20;

const STATUSES = [
  'RECEIVED',
  'AWAITING_PREPARATION',
  'PREPARATION_STARTED',
  'PREPARATION_COMPLETED',
  'PACKAGING',
  'READY_FOR_DISPATCH',
  'ON_HOLD',
  'CANCELLED',
];

export function ProductionPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const load = useCallback(
    () => productionApi.queue({ page, size: PAGE_SIZE, search: search || undefined, status: status ? [status] : [] }),
    [page, search, status],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  return (
    <div className="stack">
      <h2>Production queue</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="prod-search">Search</label>
          <input
            id="prod-search"
            type="search"
            placeholder="Order number or variant"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="prod-status">Status</label>
          <select
            id="prod-status"
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
      {loading ? <Spinner label="Loading the kitchen queue…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="Nothing to make right now" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Variant</th>
                <th>Qty</th>
                <th>Status</th>
                <th>Hold reason</th>
                <th>Received</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((job) => (
                <tr key={job.id}>
                  <td className="mono">{job.orderNumber}</td>
                  <td>{job.variantLabel}</td>
                  <td>{job.quantity}</td>
                  <td><StatusBadge status={job.status} /></td>
                  <td>{job.holdReason ?? '—'}</td>
                  <td>{dateTime(job.receivedAt)}</td>
                  <td><Link to={`/production/${job.id}`} className="link">Open</Link></td>
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
