import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { invoiceApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { dateTime, money } from '../lib/format';

const PAGE_SIZE = 20;

export function InvoicesPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const load = useCallback(
    () => invoiceApi.list({ page, size: PAGE_SIZE, search: search || undefined }),
    [page, search],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  return (
    <div className="stack">
      <h2>Invoices</h2>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="inv-search">Search</label>
          <input id="inv-search" type="search" placeholder="Invoice or order number"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }} />
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading invoices…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No invoices yet" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr><th>Invoice</th><th>Order</th><th>Customer</th><th>Total</th><th>Issued</th><th /></tr>
            </thead>
            <tbody>
              {data.content.map((invoice) => (
                <tr key={invoice.id}>
                  <td className="mono">{invoice.invoiceNumber}</td>
                  <td className="mono">{invoice.orderNumber}</td>
                  <td>{invoice.customerName}</td>
                  <td>{money(invoice.totalMinorUnits, invoice.total, invoice.currency)}</td>
                  <td>{dateTime(invoice.issuedAt)}</td>
                  <td><Link to={`/invoices/${invoice.id}`} className="link">Open</Link></td>
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
