import { useCallback, useState } from 'react';
import { dashboardApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { MetricCards, Panel } from '../components/Table';
import { metricValue } from '../lib/format';

const REPORTS = [
  { type: 'SALES', label: 'Sales' },
  { type: 'ORDERS', label: 'Orders' },
  { type: 'PAYMENTS', label: 'Payments' },
  { type: 'PRODUCTION', label: 'Production' },
  { type: 'DELIVERY', label: 'Delivery' },
  { type: 'REFUNDS', label: 'Refunds' },
  { type: 'CUSTOMERS', label: 'Customers' },
];

const WINDOWS = [7, 30, 90];

export function ReportsPage() {
  const [type, setType] = useState('SALES');
  const [days, setDays] = useState(30);

  const load = useCallback(() => dashboardApi.report(type, { days }), [type, days]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  return (
    <div className="stack">
      <h2>Reports</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="report-type">Report</label>
          <select id="report-type" value={type} onChange={(event) => setType(event.target.value)}>
            {REPORTS.map((report) => (
              <option key={report.type} value={report.type}>
                {report.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="report-window">Window</label>
          <select id="report-window" value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {WINDOWS.map((value) => (
              <option key={value} value={value}>
                Last {value} days
              </option>
            ))}
          </select>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Running the report…" /> : null}

      {data ? (
        <>
          <p className="muted small">
            {data.report} from {data.from} to {data.to}
          </p>
          <MetricCards metrics={data.metrics} />
          {data.rows.length === 0 ? (
            <Panel title="Detail">
              <p className="muted">Nothing in this window.</p>
            </Panel>
          ) : (
            <Panel title="Detail">
              <table className="table">
                <thead>
                  <tr>
                    {data.columns.map((column) => (
                      <th key={column}>{column}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data.rows.map((row, index) => (
                    <tr key={index}>
                      {row.map((cell, cellIndex) => (
                        <td key={cellIndex}>{metricValue(cell)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>
          )}
        </>
      ) : null}
    </div>
  );
}
