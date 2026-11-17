import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

export function NotificationDetailPage() {
  const { id = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => notificationApi.get(id), [id]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.NOTIFICATION_WRITE);

  async function resend() {
    setBusy(true);
    setActionError(null);
    try {
      await notificationApi.resend(id);
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner label="Loading the notification…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  const resendable = data.status === 'FAILED' || data.status === 'DEAD_LETTER';

  return (
    <div className="stack">
      <div className="row">
        <h2>{data.type.replaceAll('_', ' ')}</h2>
        <StatusBadge status={data.status} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}

      <div className="detail-grid">
        <Panel title="Delivery">
          <Field label="Channel" value={data.channel} />
          <Field label="Recipient" value={data.recipientMasked} mono />
          <Field label="Recipient type" value={data.recipientType} />
          <Field label="Attempts" value={data.attempts} />
          <Field label="Last error" value={data.lastError ?? '—'} />
          <Field label="Reference" value={data.referenceId ?? '—'} mono />
          <Field label="Created" value={dateTime(data.createdAt)} />
          <Field label="Sent" value={dateTime(data.sentAt)} />
        </Panel>
        <Panel title="Message">
          <p>
            <strong>{data.subject}</strong>
          </p>
          <p className="muted small">
            The message body is rendered before it is stored; it is not shown here to keep personal
            detail out of the dashboard. Re-sending repeats exactly what the customer would have
            received.
          </p>
        </Panel>
      </div>

      {writable ? (
        <Panel title="Resend">
          {resendable ? (
            <button type="button" className="btn btn-primary" disabled={busy} onClick={resend}>
              {busy ? 'Re-sending…' : 'Re-send this message'}
            </button>
          ) : (
            <p className="muted">
              Only a failed or dead-lettered message can be re-sent. This one is {data.status.toLowerCase()}.
            </p>
          )}
        </Panel>
      ) : null}
    </div>
  );
}
