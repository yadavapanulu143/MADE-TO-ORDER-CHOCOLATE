import { humaniseStatus } from '../lib/format';

// The colour is a hint, never the only signal: the label always carries the state,
// so the badge is still readable without colour.
export function StatusBadge({ status }: { status: string | null | undefined }) {
  const tone = toneFor(status);
  return <span className={`badge badge-${tone}`}>{humaniseStatus(status)}</span>;
}

function toneFor(status: string | null | undefined): string {
  switch (status) {
    case 'PAID':
    case 'CONFIRMED':
    case 'DELIVERED':
    case 'COMPLETED':
    case 'READY_FOR_DISPATCH':
    case 'PACKAGING_COMPLETED':
      return 'success';
    case 'FAILED':
    case 'PAYMENT_FAILED':
    case 'CANCELLED':
    case 'ATTEMPT_FAILED':
    case 'REFUND_FAILED':
      return 'danger';
    case 'PENDING':
    case 'PAYMENT_PENDING':
    case 'PLACED':
    case 'RECEIVED':
    case 'AWAITING_INGREDIENTS':
    case 'PREPARATION_ON_HOLD':
    case 'REFUND_REQUESTED':
    case 'REFUND_PENDING':
    case 'OUT_FOR_DELIVERY':
    case 'DISPATCHED':
      return 'warn';
    case 'REFUNDED':
    case 'REFUND_PROCESSED':
      return 'info';
    default:
      return 'neutral';
  }
}
