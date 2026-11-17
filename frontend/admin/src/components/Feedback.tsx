import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      <span className="spinner-dot" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message =
    error instanceof ApiError
      ? error.detail
      : error instanceof Error
        ? error.message
        : 'Something went wrong.';
  const code = error instanceof ApiError ? error.code : undefined;
  return (
    <div className="notice notice-error" role="alert">
      <p>{message}</p>
      {code ? <p className="muted small">Reference: {code}</p> : null}
      {onRetry ? (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  );
}

export function FieldErrors({ error }: { error: unknown }) {
  if (!(error instanceof ApiError)) return null;
  const entries = Object.entries(error.fieldErrors);
  if (entries.length === 0) return null;
  return (
    <ul className="field-errors" role="alert">
      {entries.map(([field, message]) => (
        <li key={field}>
          <strong>{field}</strong>: {message}
        </li>
      ))}
    </ul>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty-state">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

export function SuccessNotice({ children }: { children: ReactNode }) {
  return (
    <div className="notice notice-success" role="status">
      {children}
    </div>
  );
}
