import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { paymentApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime, money, toMinorUnits } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

export function PaymentDetailPage() {
  const { paymentId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => paymentApi.get(paymentId), [paymentId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [refundAmount, setRefundAmount] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function refund() {
    setBusy(true);
    setActionError(null);
    try {
      await paymentApi.refund(data!.id, toMinorUnits(refundAmount), refundReason);
      setRefundAmount('');
      setRefundReason('');
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner label="Loading the payment…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  const refundable = data.amountMinorUnits - data.refundedAmountMinorUnits;

  return (
    <div className="stack">
      <div className="row">
        <h2>Payment</h2>
        <StatusBadge status={data.status} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      <div className="detail-grid">
        <Panel title="Payment">
          <Field label="Amount" value={money(data.amountMinorUnits, data.amount, data.currency)} />
          <Field label="Refunded" value={money(data.refundedAmountMinorUnits, data.refundedAmount)} />
          <Field label="Provider" value={data.provider} />
          <Field label="Provider payment id" value={data.providerPaymentId ?? '—'} mono />
          <Field label="Method" value={data.method ?? '—'} />
          <Field label="Failure" value={data.failureReason ?? '—'} />
          <Field label="Created" value={dateTime(data.createdAt)} />
        </Panel>
        <Panel title="Order">
          <Field label="Order id" value={data.orderId} mono />
          <Field label="Customer id" value={data.customerId} mono />
        </Panel>
      </div>

      <Panel title="Attempts">
        {data.attempts.length === 0 ? (
          <p className="muted">No attempts recorded.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Status</th><th>Failure</th><th>When</th></tr>
            </thead>
            <tbody>
              {data.attempts.map((attempt) => (
                <tr key={attempt.id}>
                  <td><StatusBadge status={attempt.status} /></td>
                  <td>{attempt.failureReason ?? '—'}</td>
                  <td>{dateTime(attempt.attemptedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      <Panel title="Refunds">
        {data.refunds.length === 0 ? (
          <p className="muted">No refunds.</p>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Amount</th><th>Status</th><th>Reason</th><th>Requested</th><th>Processed</th></tr>
            </thead>
            <tbody>
              {data.refunds.map((refund) => (
                <tr key={refund.id}>
                  <td>{money(refund.amountMinorUnits, refund.amount)}</td>
                  <td><StatusBadge status={refund.status} /></td>
                  <td>{refund.reason ?? '—'}</td>
                  <td>{dateTime(refund.requestedAt)}</td>
                  <td>{dateTime(refund.processedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      {can(Permissions.PAYMENT_REFUND) && refundable > 0 ? (
        <Panel title="Issue a refund">
          <p className="muted">
            Up to {money(refundable)} remains refundable. The provider is called server-side; the
            browser never decides a refund succeeded.
          </p>
          <div className="field">
            <label htmlFor="refund-amount">Amount (₹)</label>
            <input
              id="refund-amount"
              inputMode="decimal"
              value={refundAmount}
              onChange={(event) => setRefundAmount(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="refund-reason">Reason</label>
            <input
              id="refund-reason"
              value={refundReason}
              onChange={(event) => setRefundReason(event.target.value)}
            />
          </div>
          <button
            type="button"
            className="btn btn-danger"
            disabled={busy || refundAmount.trim().length === 0 || refundReason.trim().length === 0}
            onClick={refund}
          >
            {busy ? 'Refunding…' : 'Refund'}
          </button>
        </Panel>
      ) : null}
    </div>
  );
}
