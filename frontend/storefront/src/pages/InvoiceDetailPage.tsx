import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { invoiceApi } from '../api/endpoints';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { formatDateTime, money } from '../lib/format';
import { useSettings } from '../lib/useSettings';
import type { Invoice } from '../api/types';

export function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams();
  const settings = useSettings();
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    invoiceApi
      .get(invoiceId)
      .then((value) => {
        if (!cancelled) setInvoice(value);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [invoiceId]);

  if (error) {
    return (
      <div className="page">
        <ErrorNotice error={error} />
        <Link to="/invoices" className="btn btn-secondary">
          Back to invoices
        </Link>
      </div>
    );
  }

  if (!invoice) return <Spinner label="Loading your invoice…" />;

  return (
    <div className="page narrow">
      <div className="invoice-actions no-print">
        <Link to="/invoices" className="btn btn-secondary">
          Back
        </Link>
        <button type="button" className="btn btn-primary" onClick={() => window.print()}>
          Print or save as PDF
        </button>
      </div>

      <article className="invoice">
        <header className="invoice-head">
          <div>
            <h1>{settings.sellerName || 'Choco'}</h1>
            {settings.contactAddress ? <p className="muted">{settings.contactAddress}</p> : null}
            {settings.contactEmail ? <p className="muted">{settings.contactEmail}</p> : null}
            {settings.contactPhone ? <p className="muted">{settings.contactPhone}</p> : null}
          </div>
          <div className="invoice-meta">
            <h2>Invoice</h2>
            <p>
              <strong>{invoice.invoiceNumber}</strong>
            </p>
            <p className="muted">Issued {formatDateTime(invoice.issuedAt)}</p>
            <p className="muted">Order {invoice.orderNumber}</p>
          </div>
        </header>

        <section className="invoice-parties">
          <div>
            <h3>Billed to</h3>
            <p>
              <strong>{invoice.customerName}</strong>
            </p>
            <p className="muted">{invoice.customerEmail}</p>
            <p className="muted">{invoice.customerMobile}</p>
          </div>
          <div>
            <h3>Delivery address</h3>
            <p>
              <strong>{invoice.billingAddress.recipientName}</strong>
            </p>
            <p className="muted">{invoice.billingAddress.formatted}</p>
            <p className="muted">{invoice.billingAddress.mobile}</p>
          </div>
        </section>

        <table className="table invoice-table">
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col">SKU</th>
              <th scope="col">Unit price</th>
              <th scope="col">Qty</th>
              <th scope="col">Line total</th>
            </tr>
          </thead>
          <tbody>
            {invoice.lines.map((line) => (
              <tr key={line.variantId}>
                <td data-label="Item">
                  {line.productName} <span className="muted small">{line.variantLabel}</span>
                </td>
                <td data-label="SKU">{line.sku}</td>
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
            <dd>{money(invoice.itemsTotalMinorUnits, invoice.itemsTotal, invoice.currency)}</dd>
          </div>
          <div>
            <dt>Delivery</dt>
            <dd>{money(invoice.deliveryChargeMinorUnits, invoice.deliveryCharge, invoice.currency)}</dd>
          </div>
          <div className="grand-total">
            <dt>Total paid</dt>
            <dd>{money(invoice.totalMinorUnits, invoice.total, invoice.currency)}</dd>
          </div>
        </dl>

        <footer className="invoice-foot muted small">
          <p>Thank you for your order. This is a computer-generated invoice.</p>
          {settings.termsAndConditions ? <p>{settings.termsAndConditions}</p> : null}
        </footer>
      </article>
    </div>
  );
}
