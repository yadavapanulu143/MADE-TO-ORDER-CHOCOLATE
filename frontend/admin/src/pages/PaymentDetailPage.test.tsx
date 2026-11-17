import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PaymentDetailPage } from './PaymentDetailPage';
import { renderAtRoute, signedIn, stubFetch } from '../test/helpers';

const PAYMENT = {
  id: 'p1',
  orderId: 'o1',
  customerId: 'c1',
  amountMinorUnits: 94900,
  amount: '₹949.00',
  currency: 'INR',
  status: 'PAID',
  provider: 'razorpay',
  providerOrderId: 'order_1',
  providerPaymentId: 'pay_1',
  method: 'UPI',
  failureReason: null,
  refundedAmountMinorUnits: 0,
  refundedAmount: '₹0.00',
  createdAt: '2026-03-01T10:00:00Z',
  updatedAt: '2026-03-01T10:05:00Z',
  refunds: [],
  attempts: [],
};

describe('PaymentDetailPage', () => {
  it('shows the payment and its refundable remainder', async () => {
    signedIn();
    stubFetch({ 'GET /api/v1/admin/payments/p1': { body: PAYMENT } });

    renderAtRoute(<PaymentDetailPage />, { route: '/payments/p1', path: '/payments/:paymentId' });

    expect(await screen.findByText('razorpay')).toBeInTheDocument();
    expect(screen.getByText(/remains refundable/i)).toBeInTheDocument();
  });

  it('sends a refund in integer paise, not rupees', async () => {
    signedIn();
    const calls = stubFetch({
      'GET /api/v1/admin/payments/p1': { body: PAYMENT },
      'POST /api/v1/admin/payments/p1/refunds': {
        body: { refundId: 'r1', paymentId: 'p1', amountMinorUnits: 10000, amount: '₹100.00', status: 'PROCESSING', providerRefundId: null },
      },
    });

    renderAtRoute(<PaymentDetailPage />, { route: '/payments/p1', path: '/payments/:paymentId' });

    await userEvent.type(await screen.findByLabelText('Amount (₹)'), '100');
    await userEvent.type(screen.getByLabelText('Reason'), 'Damaged on arrival');
    await userEvent.click(screen.getByRole('button', { name: 'Refund' }));

    await waitFor(() => {
      const call = calls.find((entry) => entry.method === 'POST');
      expect(call?.body).toMatchObject({ amountMinorUnits: 10000, reason: 'Damaged on arrival' });
    });
  });
});
