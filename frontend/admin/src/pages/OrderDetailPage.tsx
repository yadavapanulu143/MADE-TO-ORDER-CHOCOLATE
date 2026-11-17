import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { orderApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

// The preparation and delivery steps the order service accepts, and the permission
// each needs. The server refuses an illegal transition regardless of what is shown.
const PREPARATION_ACTIONS = ['START', 'HOLD', 'RESUME', 'COMPLETE', 'PACKAGING', 'READY_FOR_DISPATCH'];
const DELIVERY_ACTIONS = ['OUT_FOR_DELIVERY', 'DELIVERED', 'ATTEMPT_FAILED', 'RESCHEDULED', 'RETURNED'];

export function OrderDetailPage() {
  const { orderId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => orderApi.get(orderId), [orderId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [personName, setPersonName] = useState('');
  const [personPhone, setPersonPhone] = useState('');

  async function run(work: () => Promise<unknown>) {
    setBusy(true);
    setActionError(null);
    try {
      await work();
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner label="Loading the order…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <div className="row">
        <h2 className="mono">{data.orderNumber}</h2>
        <StatusBadge status={data.status} />
        <StatusBadge status={data.paymentStatus} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      <div className="detail-grid">
        <Panel title="Customer">
          <Field label="Name" value={data.customerName} />
          <Field label="Email" value={data.customerEmail} />
          <Field label="Mobile" value={data.customerMobile} />
        </Panel>
        <Panel title="Delivery address">
          <p>{data.deliveryAddress.formatted}</p>
          <Field label="Mobile" value={data.deliveryAddress.mobile} />
        </Panel>
        <Panel title="Progress">
          <Field label="Preparation" value={<StatusBadge status={data.preparationStatus} />} />
          <Field label="Delivery" value={<StatusBadge status={data.deliveryStatus} />} />
          <Field label="Delivery person" value={data.deliveryPersonName ?? '—'} />
          <Field label="Dispatched" value={dateTime(data.dispatchedAt)} />
          <Field label="Delivered" value={dateTime(data.deliveredAt)} />
        </Panel>
      </div>

      <Panel title="Items">
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Variant</th>
              <th>SKU</th>
              <th>Unit</th>
              <th>Qty</th>
              <th>Line</th>
            </tr>
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

      {can(Permissions.PRODUCTION_WRITE) ? (
        <Panel title="Preparation">
          <div className="row">
            {PREPARATION_ACTIONS.map((action) => (
              <button
                key={action}
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => run(() => orderApi.preparation(data.id, action, reason || undefined))}
              >
                {action.replaceAll('_', ' ')}
              </button>
            ))}
          </div>
          <div className="field">
            <label htmlFor="prep-reason">Reason (for a hold)</label>
            <input
              id="prep-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>
        </Panel>
      ) : null}

      {can(Permissions.DELIVERY_WRITE) ? (
        <Panel title="Delivery">
          <div className="field">
            <label htmlFor="person">Delivery person</label>
            <input id="person" value={personName} onChange={(e) => setPersonName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="phone">Phone</label>
            <input id="phone" value={personPhone} onChange={(e) => setPersonPhone(e.target.value)} />
          </div>
          <div className="row">
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy || personName.trim().length === 0}
              onClick={() =>
                run(() => orderApi.assignDelivery(data.id, personName, personPhone || undefined))
              }
            >
              Assign delivery
            </button>
            {DELIVERY_ACTIONS.map((action) => (
              <button
                key={action}
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => run(() => orderApi.delivery(data.id, action, reason || undefined))}
              >
                {action.replaceAll('_', ' ')}
              </button>
            ))}
          </div>
        </Panel>
      ) : null}

      {can(Permissions.ORDER_WRITE) ? (
        <Panel title="Notes and cancellation">
          <div className="field">
            <label htmlFor="note">Add a note</label>
            <input id="note" value={note} onChange={(event) => setNote(event.target.value)} />
          </div>
          <div className="row">
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy || note.trim().length === 0}
              onClick={() => run(async () => { await orderApi.addNote(data.id, note); setNote(''); })}
            >
              Add note
            </button>
            <button
              type="button"
              className="btn btn-danger"
              disabled={busy}
              onClick={() => run(() => orderApi.cancel(data.id, reason || 'Cancelled by the seller'))}
            >
              Cancel order
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => run(() => orderApi.allowPaymentRetry(data.id))}
            >
              Allow payment retry
            </button>
          </div>
        </Panel>
      ) : null}

      <div className="detail-grid">
        <Panel title="History">
          <ul>
            {data.statusHistory.map((change) => (
              <li key={`${change.toStatus}-${change.occurredAt}`}>
                <StatusBadge status={change.toStatus} /> {dateTime(change.occurredAt)}
                {change.reason ? <span className="muted"> — {change.reason}</span> : null}
              </li>
            ))}
          </ul>
        </Panel>
        <Panel title="Notes">
          {data.notes.length === 0 ? (
            <p className="muted">No notes.</p>
          ) : (
            <ul>
              {data.notes.map((item) => (
                <li key={item.id}>
                  {item.text} <span className="muted small">{dateTime(item.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}
