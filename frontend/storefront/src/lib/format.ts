/** Money and date formatting helpers. The backend sends paise; the UI formats them. */

export function formatMoney(minorUnits: number, currency = 'INR'): string {
  const symbol = currency === 'INR' ? '\u20b9' : `${currency} `;
  const sign = minorUnits < 0 ? '-' : '';
  const value = Math.abs(minorUnits);
  const major = Math.floor(value / 100);
  const minor = value % 100;
  const grouped = major.toLocaleString('en-IN');
  return `${sign}${symbol}${grouped}.${String(minor).padStart(2, '0')}`;
}

/** Prefer the server's own formatted string so the storefront never disagrees with a receipt. */
export function money(minorUnits: number, formatted: string | null | undefined, currency = 'INR'): string {
  return formatted && formatted.length > 0 ? formatted : formatMoney(minorUnits, currency);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '\u2014';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '\u2014';
  return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '\u2014';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '\u2014';
  return date.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** A human label for a SCREAMING_SNAKE status the backend uses. */
export function humaniseStatus(status: string | null | undefined): string {
  if (!status) return '\u2014';
  return status
    .toLowerCase()
    .split('_')
    .map((word) => (word.length === 0 ? word : word[0]!.toUpperCase() + word.slice(1)))
    .join(' ');
}

export function pincodeValid(pincode: string): boolean {
  return /^[1-9][0-9]{5}$/.test(pincode.trim());
}

export function mobileValid(mobile: string): boolean {
  return /^(\+91|91|0)?[6-9][0-9]{9}$/.test(mobile.trim());
}
