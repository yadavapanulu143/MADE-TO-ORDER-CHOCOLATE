import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { invoiceApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { dateTime, money } from '../lib/format';

export function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams();
  const load = useCallback(() => invoiceApi.get(invoiceId), [invoiceId]);
  const { data, error, loading, reload } = useAsync(load, [load]);
  const [printing, setPrinting] = useState(false);

  if (loading) return <Spinner label="Loading the invoice…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <div className="row">
        <h2 className="mono">{data.invoiceNumber}</h2>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={printing}
          onClick={() => {
            setPrinting(true);
            window.print();
            setPrinting(false);
          }}
        >
          Print
        </button>
      </div>

      <div className="detail-grid">
        <Panel title="Billed to">
          <p>{data.customerName}</p>
          <p>{data.customerEmail}</p>
          <p>{data.customerMobile}</p>
          <p>{data.billingAddress.formatted}</p>
        </Panel>
        <Panel title="Invoice">
          <Field label="Order" value={data.orderNumber} mono />
          <Field label="Issued" value={dateTime(data.issuedAt)} />
        </Panel>
      </div>

      <Panel title="Items">
        <table className="table">
          <thead>
            <tr><th>Product</th><th>Variant</th><th>SKU</th><th>Unit</th><th>Qty</th><th>Line</th></tr>
          </thead>
          <tbody>
            {data.lines.map((line) => (
              <tr key={`${line.variantId}-${line.sku}`}>
                <td>{line.productName}</td>
                <td>{line.variantLabel}</td>
                <td className="mono">{line.sku}</td>
                <td>{money(line.unitPriceMinorUnits, line.unitPrice)}</td>
                <td>{line.quantity}</td>
                <td>{money(line.lineTotalMinorUnits, line.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={5}>Items</td>
              <td>{money(data.itemsTotalMinorUnits, data.itemsTotal)}</td>
            </tr>
            <tr>
              <td colSpan={5}>Delivery</td>
              <td>{money(data.deliveryChargeMinorUnits, data.deliveryCharge)}</td>
            </tr>
            <tr>
              <td colSpan={5}>
                <strong>Total</strong>
              </td>
              <td>
                <strong>{money(data.totalMinorUnits, data.total)}</strong>
              </td>
            </tr>
          </tfoot>
        </table>
      </Panel>
    </div>
  );
}
