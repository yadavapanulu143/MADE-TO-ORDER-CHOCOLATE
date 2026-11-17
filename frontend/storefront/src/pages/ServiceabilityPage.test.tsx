import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ServiceabilityPage } from './ServiceabilityPage';
import { renderWithProviders, stubFetch } from '../test/helpers';

describe('ServiceabilityPage', () => {
  it('reports a covered PIN code with its delivery charge', async () => {
    stubFetch({
      'GET /api/v1/serviceability/560001': {
        body: {
          pincode: '560001',
          serviceable: true,
          message: 'We deliver here',
          deliveryChargeMinorUnits: 4900,
          deliveryCharge: '₹49.00',
          currency: 'INR',
        },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<ServiceabilityPage />);
    await user.type(screen.getByLabelText('PIN code'), '560001');
    await user.click(screen.getByRole('button', { name: 'Check' }));

    expect(await screen.findByText(/we deliver to 560001/i)).toBeInTheDocument();
    expect(screen.getByText(/₹49\.00/)).toBeInTheDocument();
  });

  it('explains a PIN code we do not reach rather than failing', async () => {
    stubFetch({
      'GET /api/v1/serviceability/999999': {
        body: {
          pincode: '999999',
          serviceable: false,
          message: 'We do not deliver to this PIN code yet',
          deliveryChargeMinorUnits: 0,
          deliveryCharge: '₹0.00',
          currency: 'INR',
        },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<ServiceabilityPage />);
    await user.type(screen.getByLabelText('PIN code'), '999999');
    await user.click(screen.getByRole('button', { name: 'Check' }));

    expect(await screen.findByText('We do not deliver to this PIN code yet')).toBeInTheDocument();
  });

  it('keeps the check disabled until the PIN code looks valid', async () => {
    stubFetch({});
    const user = userEvent.setup();

    renderWithProviders(<ServiceabilityPage />);
    expect(screen.getByRole('button', { name: 'Check' })).toBeDisabled();

    await user.type(screen.getByLabelText('PIN code'), '560001');
    expect(screen.getByRole('button', { name: 'Check' })).toBeEnabled();
  });
});
