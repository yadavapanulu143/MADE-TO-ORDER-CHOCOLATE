// Formatting helpers. Money is always integer paise from the backend; the server's
// own formatted string wins when it is present, so a dashboard figure cannot
// disagree with an invoice.

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Render integer paise as rupees, preferring the server's formatted string. */
export function money(
  minorUnits: number | null | undefined,
  formatted?: string | null,
  _currency?: string | null,
): string {
  if (formatted) return formatted;
  if (minorUnits === null || minorUnits === undefined || Number.isNaN(minorUnits)) return '—';
  return inr.format(minorUnits / 100);
}

/** Render a paise value the caller typed in rupees back to integer paise. */
export function toMinorUnits(rupees: string | number): number {
  const value = typeof rupees === 'number' ? rupees : Number.parseFloat(rupees);
  if (Number.isNaN(value)) return 0;
  return Math.round(value * 100);
}

/** Render a minor-unit count as a plain rupee string (no symbol), for inputs. */
export function toRupeeInput(minorUnits: number | null | undefined): string {
  if (minorUnits === null || minorUnits === undefined) return '';
  return (minorUnits / 100).toFixed(2);
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function date(value: string | null | undefined): string {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return '—';
  return parsed.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

/** Turn SCREAMING_SNAKE_CASE into readable words. */
export function humaniseStatus(status: string | null | undefined): string {
  if (!status) return '—';
  return status
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/** Render a metric value the report returned: money stays exact, lists are joined. */
export function metricValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (Array.isArray(value)) return value.map((v) => String(v)).join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
