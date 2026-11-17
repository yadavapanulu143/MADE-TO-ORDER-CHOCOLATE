import { useState } from 'react';
import { dashboardApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { ErrorNotice, SuccessNotice } from '../components/Feedback';
import { Panel } from '../components/Table';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

// Each export names the permission the backend checks. The customer export carries
// masked contact detail and needs its own permission, not just report access.
const EXPORTS: Array<{ type: string; label: string; permission: string; description: string }> = [
  {
    type: 'ORDERS',
    label: 'Orders',
    permission: Permissions.REPORT_READ,
    description: 'Every order with its state and totals.',
  },
  {
    type: 'PAYMENTS',
    label: 'Payments',
    permission: Permissions.REPORT_READ,
    description: 'Payments received, failed and pending.',
  },
  {
    type: 'INVOICES',
    label: 'Invoices',
    permission: Permissions.REPORT_READ,
    description: 'Issued invoices with their billed totals.',
  },
  {
    type: 'CUSTOMERS',
    label: 'Customers',
    permission: Permissions.CUSTOMER_EXPORT,
    description: 'Customers and their verification state. Contact detail is masked.',
  },
];

const WINDOWS = [7, 30, 90];

export function ExportsPage() {
  const { can } = useAdminAuth();
  const [days, setDays] = useState(30);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  async function download(type: string, format: 'csv' | 'xlsx') {
    setBusy(`${type}-${format}`);
    setError(null);
    setMessage(null);
    try {
      const { blob, filename } = await dashboardApi.download(type, format, days);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
      setMessage(`${filename} downloaded.`);
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="stack">
      <h2>Exports</h2>
      <p className="muted">
        Downloads are generated server-side and each one is recorded in the audit log. Password
        hashes, OTPs and tokens are never part of an export.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="export-window">Window</label>
          <select id="export-window" value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {WINDOWS.map((value) => (
              <option key={value} value={value}>
                Last {value} days
              </option>
            ))}
          </select>
        </div>
      </div>

      {error ? (
        <ErrorNotice error={error} />
      ) : null}
      {error instanceof ApiError && error.status === 403 ? (
        <p className="muted small">
          The customer export needs the <code>customer:export</code> permission.
        </p>
      ) : null}
      {message ? <SuccessNotice>{message}</SuccessNotice> : null}

      <div className="detail-grid">
        {EXPORTS.map((item) => (
          <Panel key={item.type} title={item.label}>
            <p className="muted small">{item.description}</p>
            {can(item.permission) ? (
              <div className="row">
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={busy !== null}
                  onClick={() => download(item.type, 'csv')}
                >
                  {busy === `${item.type}-csv` ? 'Preparing…' : 'CSV'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={busy !== null}
                  onClick={() => download(item.type, 'xlsx')}
                >
                  {busy === `${item.type}-xlsx` ? 'Preparing…' : 'XLSX'}
                </button>
              </div>
            ) : (
              <p className="muted small">You do not have permission to export this dataset.</p>
            )}
          </Panel>
        ))}
      </div>
    </div>
  );
}
