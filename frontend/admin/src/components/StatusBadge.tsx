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
    case 'CAPTURED':
    case 'CONFIRMED':
    case 'DELIVERED':
    case 'COMPLETED':
    case 'PREPARATION_COMPLETED':
    case 'PACKAGING':
    case 'READY_FOR_DISPATCH':
    case 'SENT':
    case 'ACTIVE':
    case 'PUBLISHED':
      return 'success';
    case 'FAILED':
    case 'PAYMENT_FAILED':
    case 'CANCELLED':
    case 'ATTEMPT_FAILED':
    case 'REFUND_FAILED':
    case 'DEAD_LETTER':
    case 'ARCHIVED':
    case 'RETURNED':
    case 'ON_HOLD':
      return 'danger';
    case 'PENDING':
    case 'PAYMENT_PENDING':
    case 'PLACED':
    case 'RECEIVED':
    case 'AWAITING_PREPARATION':
    case 'PREPARATION_STARTED':
    case 'OUT_FOR_DELIVERY':
    case 'ASSIGNED':
    case 'RESCHEDULED':
    case 'DRAFT':
      return 'warn';
    case 'REFUNDED':
    case 'REFUND_PROCESSED':
    case 'PROCESSING':
    case 'IN_PREPARATION':
      return 'info';
    default:
      return 'neutral';
  }
}
