import { describe, expect, it } from 'vitest';
import { formatDate, formatDateTime, formatMoney, humaniseStatus, mobileValid, money, pincodeValid } from './format';

describe('formatMoney', () => {
  it('renders paise as rupees with two decimals', () => {
    expect(formatMoney(0)).toBe('\u20b90.00');
    expect(formatMoney(5)).toBe('\u20b90.05');
    expect(formatMoney(123456)).toBe('\u20b91,234.56');
  });

  it('groups large amounts in the Indian system', () => {
    expect(formatMoney(1234567890)).toBe('\u20b91,23,45,678.90');
  });

  it('keeps a negative sign outside the symbol', () => {
    expect(formatMoney(-2500)).toBe('-\u20b925.00');
  });
});

describe('money', () => {
  it('prefers the server-formatted string so a receipt cannot disagree', () => {
    expect(money(1000, 'Rs 10.00')).toBe('Rs 10.00');
  });

  it('falls back to local formatting when the server sent nothing', () => {
    expect(money(1000, null)).toBe('\u20b910.00');
    expect(money(1000, '')).toBe('\u20b910.00');
  });
});

describe('humaniseStatus', () => {
  it('turns a status code into words', () => {
    expect(humaniseStatus('PAYMENT_PENDING')).toBe('Payment Pending');
    expect(humaniseStatus('PAID')).toBe('Paid');
  });

  it('handles an absent status', () => {
    expect(humaniseStatus(null)).toBe('\u2014');
    expect(humaniseStatus(undefined)).toBe('\u2014');
  });
});

describe('date formatting', () => {
  it('formats a valid instant', () => {
    expect(formatDate('2026-02-01T10:00:00Z')).toMatch(/2026/);
    expect(formatDateTime('2026-02-01T10:00:00Z')).toMatch(/2026/);
  });

  it('degrades gracefully for a missing or invalid value', () => {
    expect(formatDate(null)).toBe('\u2014');
    expect(formatDate('not-a-date')).toBe('\u2014');
    expect(formatDateTime(undefined)).toBe('\u2014');
  });
});

describe('input validators', () => {
  it('accepts Indian PIN codes and rejects malformed ones', () => {
    expect(pincodeValid('560001')).toBe(true);
    expect(pincodeValid('012345')).toBe(false);
    expect(pincodeValid('56000')).toBe(false);
    expect(pincodeValid('5600011')).toBe(false);
  });

  it('accepts Indian mobile numbers with common prefixes', () => {
    expect(mobileValid('9876543210')).toBe(true);
    expect(mobileValid('+919876543210')).toBe(true);
    expect(mobileValid('09876543210')).toBe(true);
    expect(mobileValid('1234567890')).toBe(false);
  });
});
