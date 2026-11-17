import { describe, expect, it } from 'vitest';
import { money, toMinorUnits, toRupeeInput, humaniseStatus, metricValue } from './format';

describe('format', () => {
  it('prefers the server-formatted money string', () => {
    expect(money(94900, '₹949.00')).toBe('₹949.00');
  });

  it('formats integer paise as rupees when no server string is present', () => {
    expect(money(94900)).toBe('₹949.00');
  });

  it('renders an em dash for a missing amount', () => {
    expect(money(null)).toBe('—');
    expect(money(undefined)).toBe('—');
  });

  it('converts a rupee input to integer paise without floating-point drift', () => {
    expect(toMinorUnits('100')).toBe(10000);
    expect(toMinorUnits('99.99')).toBe(9999);
    expect(toMinorUnits('0.1')).toBe(10);
  });

  it('renders a paise value as a rupee input string', () => {
    expect(toRupeeInput(94900)).toBe('949.00');
    expect(toRupeeInput(null)).toBe('');
  });

  it('humanises a status', () => {
    expect(humaniseStatus('PAYMENT_FAILED')).toBe('Payment Failed');
    expect(humaniseStatus(null)).toBe('—');
  });

  it('renders a metric value of any shape', () => {
    expect(metricValue('₹12,000.00')).toBe('₹12,000.00');
    expect(metricValue(42)).toBe('42');
    expect(metricValue(['a', 'b'])).toBe('a, b');
    expect(metricValue(null)).toBe('—');
  });
});
