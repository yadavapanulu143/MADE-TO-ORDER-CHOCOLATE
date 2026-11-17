import type { ReactNode } from 'react';
import type { ReportMetric } from '../api/types';
import { metricValue } from '../lib/format';

/** A row of headline figures, as the dashboard and reports return them. */
export function MetricCards({ metrics }: { metrics: ReportMetric[] }) {
  if (metrics.length === 0) return null;
  return (
    <div className="cards">
      {metrics.map((metric) => (
        <div className="card" key={metric.key}>
          <p className="metric-label">{metric.label}</p>
          <p className="metric-value">{metricValue(metric.value)}</p>
        </div>
      ))}
    </div>
  );
}

/** A simple pager over a page of a PageResult. */
export function Pager({
  page,
  totalElements,
  size,
  onPage,
}: {
  page: number;
  totalElements: number;
  size: number;
  onPage: (page: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  if (totalPages <= 1) return null;
  return (
    <nav className="pager" aria-label="Pagination">
      <button
        type="button"
        className="btn btn-secondary"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
      >
        Previous
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="btn btn-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        Next
      </button>
    </nav>
  );
}

/** A titled panel; a small layout helper so pages stay short. */
export function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="panel">
      <h3>{title}</h3>
      {children}
    </section>
  );
}

export function Field({
  label,
  value,
  mono,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="field">
      <span className="metric-label">{label}</span>
      <span className={mono ? 'mono' : undefined}>{value}</span>
    </div>
  );
}
