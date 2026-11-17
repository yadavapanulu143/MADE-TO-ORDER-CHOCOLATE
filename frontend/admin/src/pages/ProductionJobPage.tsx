import { useCallback, useState } from 'react';
import { useParams } from 'react-router-dom';
import { productionApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { dateTime } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

// The preparation steps the fulfilment service accepts. The server refuses an
// illegal transition regardless of what is shown here.
const ACTIONS = [
  'ACKNOWLEDGE',
  'START',
  'HOLD',
  'RESUME',
  'COMPLETE',
  'PACKAGING',
  'READY_FOR_DISPATCH',
  'CANCEL',
];

export function ProductionJobPage() {
  const { jobId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(() => productionApi.get(jobId), [jobId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

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

  if (loading) return <Spinner label="Loading the job…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  const writable = can(Permissions.PRODUCTION_WRITE);

  return (
    <div className="stack">
      <div className="row">
        <h2 className="mono">{data.orderNumber}</h2>
        <StatusBadge status={data.status} />
      </div>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      <div className="detail-grid">
        <Panel title="Job">
          <Field label="Variant" value={data.variantLabel} />
          <Field label="Quantity" value={data.quantity} />
          <Field label="Ingredients consumed" value={data.ingredientsConsumed ? 'Yes' : 'Not yet'} />
          <Field label="Hold reason" value={data.holdReason ?? '—'} />
          <Field label="Received" value={dateTime(data.receivedAt)} />
          <Field label="Updated" value={dateTime(data.updatedAt)} />
        </Panel>
      </div>

      {writable ? (
        <Panel title="Advance preparation">
          <div className="field">
            <label htmlFor="reason">Reason (for a hold)</label>
            <input id="reason" value={reason} onChange={(event) => setReason(event.target.value)} />
          </div>
          <div className="row">
            {ACTIONS.map((action) => (
              <button
                key={action}
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => run(() => productionApi.advance(data.id, action, reason || undefined))}
              >
                {action.replaceAll('_', ' ')}
              </button>
            ))}
          </div>
        </Panel>
      ) : null}

      {writable ? (
        <Panel title="Notes">
          <div className="field">
            <label htmlFor="note">Add a note</label>
            <input id="note" value={note} onChange={(event) => setNote(event.target.value)} />
          </div>
          <button
            type="button"
            className="btn btn-secondary"
            disabled={busy || note.trim().length === 0}
            onClick={() => run(async () => { await productionApi.addNote(data.id, note); setNote(''); })}
          >
            Add note
          </button>
        </Panel>
      ) : null}

      <Panel title="Notes on this job">
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
  );
}
