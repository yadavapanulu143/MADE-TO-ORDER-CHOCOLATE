import { useCallback, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { inventoryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { dateTime } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

const LEDGER_REASONS = ['RECEIVED', 'CONSUMED', 'ADJUSTED', 'WASTED'];

export function IngredientDetailPage() {
  const { ingredientId = '' } = useParams();
  const { can } = useAdminAuth();
  const load = useCallback(
    async () => {
      const [ledger] = await Promise.all([inventoryApi.ledger(ingredientId, { size: 50 })]);
      return ledger;
    },
    [ingredientId],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('RECEIVED');
  const [note, setNote] = useState('');
  const [minimumLevel, setMinimumLevel] = useState('');
  const [active, setActive] = useState(true);
  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.SETTINGS_WRITE);

  async function adjust(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setActionError(null);
    try {
      await inventoryApi.adjustStock(ingredientId, {
        amount: Number(amount),
        reason,
        note: note || undefined,
      });
      setAmount('');
      setNote('');
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  async function saveSettings(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setActionError(null);
    try {
      await inventoryApi.update(ingredientId, {
        minimumLevel: Number(minimumLevel),
        active,
      });
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner label="Loading the stock ledger…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <h2>Stock ledger</h2>

      {actionError ? <ErrorNotice error={actionError} /> : null}
      <FieldErrors error={actionError} />

      {writable ? (
        <div className="detail-grid">
          <Panel title="Record a movement">
            <form className="form" onSubmit={adjust}>
              <div className="field">
                <label htmlFor="amount">Amount (negative to remove)</label>
                <input
                  id="amount"
                  inputMode="decimal"
                  required
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="reason">Reason</label>
                <select id="reason" value={reason} onChange={(event) => setReason(event.target.value)}>
                  {LEDGER_REASONS.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="note">Note</label>
                <input id="note" value={note} onChange={(event) => setNote(event.target.value)} />
              </div>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Record movement
              </button>
            </form>
          </Panel>
          <Panel title="Low-stock threshold">
            <form className="form" onSubmit={saveSettings}>
              <div className="field">
                <label htmlFor="minimum">Minimum level</label>
                <input
                  id="minimum"
                  inputMode="decimal"
                  required
                  value={minimumLevel}
                  onChange={(event) => setMinimumLevel(event.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="active">
                  <input
                    id="active"
                    type="checkbox"
                    checked={active}
                    onChange={(event) => setActive(event.target.checked)}
                  />{' '}
                  Active
                </label>
              </div>
              <button type="submit" className="btn btn-secondary" disabled={busy}>
                Save
              </button>
            </form>
          </Panel>
        </div>
      ) : null}

      <Panel title="Movements">
        {data.content.length === 0 ? (
          <p className="muted">No movements recorded.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Reason</th>
                <th>Change</th>
                <th>Balance after</th>
                <th>Note</th>
                <th>When</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((entry) => (
                <tr key={entry.id}>
                  <td>{entry.reason}</td>
                  <td>{entry.delta}</td>
                  <td>{entry.balanceAfter}</td>
                  <td>{entry.note ?? '—'}</td>
                  <td>{dateTime(entry.occurredAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      <div className="detail-grid">
        <Panel title="Ingredient">
          <Field label="Id" value={ingredientId} mono />
        </Panel>
      </div>
    </div>
  );
}
