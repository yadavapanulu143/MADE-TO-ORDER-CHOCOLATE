import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { invoiceApi } from '../api/endpoints';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { formatDate, money } from '../lib/format';
import type { Invoice } from '../api/types';

export function InvoicesPage() {
  const [invoices, setInvoices] = useState<Invoice[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    invoiceApi
      .list()
      .then((value) => {
        if (!cancelled) setInvoices(value);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page">
      <h1>Your invoices</h1>
      {error ? <ErrorNotice error={error} /> : null}
      {!invoices && !error ? <Spinner label="Loading your invoices…" /> : null}

      {invoices && invoices.length === 0 ? (
        <EmptyState title="No invoices yet">
          <p className="muted">An invoice appears here once a paid order has been billed.</p>
          <Link to="/products" className="btn btn-primary">
            Browse chocolate
          </Link>
        </EmptyState>
      ) : null}

      {invoices && invoices.length > 0 ? (
        <ul className="invoice-list">
          {invoices.map((invoice) => (
            <li key={invoice.id} className="invoice-row">
              <Link to={`/invoices/${invoice.id}`}>
                <span className="invoice-number">{invoice.invoiceNumber}</span>
                <span className="muted">Order {invoice.orderNumber}</span>
                <span className="muted">{formatDate(invoice.issuedAt)}</span>
                <span className="price">{money(invoice.totalMinorUnits, invoice.total, invoice.currency)}</span>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
