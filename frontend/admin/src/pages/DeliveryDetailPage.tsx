import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { deliveryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

// The delivery steps the fulfilment service accepts. The server refuses an illegal
// transition regardless of what is shown.
const ACTIONS = ['OUT_FOR_DELIVERY', 'DELIVERED', 'ATTEMPT_FAILED', 'RESCHEDULED', 'RETURNED', 'CANCEL'];

export function DeliveryDetailPage() {
  const { orderId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => deliveryApi.forOrder(orderId), [orderId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [personName, setPersonName] = useState('');
  const [personPhone, setPersonPhone] = useState('');
  const [reason, setReason] = useState('');
  const [proofReference, setProofReference] = useState('');
  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.DELIVERY_WRITE);

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

  if (loading) return <Spinner label="Loading the delivery…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <div className="row">
        <h2 className="mono">{data.orderNumber}</h2>
        <StatusBadge status={data.status} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      <div className="detail-grid">
        <Panel title="Deliver to">
          <p>{data.address.formatted}</p>
          <Field label="Recipient" value={data.address.recipientName} />
          <Field label="Mobile" value={data.address.mobile} />
          <Field label="Charge" value={money(data.deliveryChargeMinorUnits, data.deliveryCharge, data.currency)} />
        </Panel>
        <Panel title="Delivery">
          <Field label="Delivery person" value={data.deliveryPersonName ?? '—'} />
          <Field label="Phone" value={data.deliveryPersonPhone ?? '—'} />
          <Field label="Attempts" value={data.attemptCount} />
          <Field label="Dispatched" value={dateTime(data.dispatchedAt)} />
          <Field label="Delivered" value={dateTime(data.deliveredAt)} />
          <Field label="Proof" value={data.proofReference ?? '—'} />
        </Panel>
      </div>

      {writable ? (
        <Panel title="Assign and progress">
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
                run(() =>
                  deliveryApi.assign(data.orderId, {
                    deliveryPersonName: personName,
                    deliveryPersonPhone: personPhone || undefined,
                  }),
                )
              }
            >
              Assign
            </button>
          </div>

          <div className="field">
            <label htmlFor="reason">Reason / notes</label>
            <input id="reason" value={reason} onChange={(e) => setReason(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="proof">Proof reference (for a delivery)</label>
            <input id="proof" value={proofReference} onChange={(e) => setProofReference(e.target.value)} />
          </div>
          <div className="row">
            {ACTIONS.map((action) => (
              <button
                key={action}
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() =>
                  run(() =>
                    deliveryApi.progress(data.orderId, {
                      action,
                      reason: reason || undefined,
                      proofReference: proofReference || undefined,
                    }),
                  )
                }
              >
                {action.replaceAll('_', ' ')}
              </button>
            ))}
          </div>
        </Panel>
      ) : null}

      <Panel title="Attempts">
        {data.attempts.length === 0 ? (
          <p className="muted">No attempts recorded.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Outcome</th><th>Reason</th><th>Notes</th><th>When</th></tr>
            </thead>
            <tbody>
              {data.attempts.map((attempt) => (
                <tr key={attempt.id}>
                  <td><StatusBadge status={attempt.outcome} /></td>
                  <td>{attempt.reason ?? '—'}</td>
                  <td>{attempt.notes ?? '—'}</td>
                  <td>{dateTime(attempt.attemptedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
