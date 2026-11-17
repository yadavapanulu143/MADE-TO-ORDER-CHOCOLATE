import { useState } from 'react';
import { FieldErrors, ErrorNotice } from './Feedback';
import { mobileValid, pincodeValid } from '../lib/format';
import type { Address, AddressInput } from '../api/types';

interface Props {
  initial?: Address | null;
  onSave: (input: AddressInput) => Promise<void>;
  onCancel?: () => void;
}

const EMPTY: AddressInput = {
  recipientName: '',
  mobile: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  pincode: '',
  deliveryInstructions: '',
  defaultAddress: false,
};

export function AddressForm({ initial, onSave, onCancel }: Props) {
  const [form, setForm] = useState<AddressInput>(
    initial
      ? {
          recipientName: initial.recipientName,
          mobile: initial.mobile,
          line1: initial.line1,
          line2: initial.line2 ?? '',
          city: initial.city,
          state: initial.state,
          pincode: initial.pincode,
          deliveryInstructions: initial.deliveryInstructions ?? '',
          defaultAddress: initial.isDefault,
        }
      : EMPTY,
  );
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  function update<K extends keyof AddressInput>(field: K, value: AddressInput[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await onSave(form);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="form address-form">
      <div className="field-row">
        <label>
          Recipient name
          <input
            type="text"
            required
            minLength={2}
            maxLength={120}
            value={form.recipientName}
            onChange={(event) => update('recipientName', event.target.value)}
          />
        </label>
        <label>
          Mobile number
          <input
            type="tel"
            required
            value={form.mobile}
            onChange={(event) => update('mobile', event.target.value)}
            aria-describedby="addr-mobile-hint"
          />
          {form.mobile && !mobileValid(form.mobile) ? (
            <span id="addr-mobile-hint" className="field-hint error">
              Enter a valid 10-digit Indian mobile number
            </span>
          ) : null}
        </label>
      </div>

      <label>
        Address line 1
        <input
          type="text"
          required
          maxLength={200}
          value={form.line1}
          onChange={(event) => update('line1', event.target.value)}
        />
      </label>
      <label>
        Address line 2 <span className="muted small">(optional)</span>
        <input
          type="text"
          maxLength={200}
          value={form.line2}
          onChange={(event) => update('line2', event.target.value)}
        />
      </label>

      <div className="field-row">
        <label>
          City
          <input
            type="text"
            required
            maxLength={200}
            value={form.city}
            onChange={(event) => update('city', event.target.value)}
          />
        </label>
        <label>
          State
          <input
            type="text"
            required
            maxLength={200}
            value={form.state}
            onChange={(event) => update('state', event.target.value)}
          />
        </label>
        <label>
          PIN code
          <input
            type="text"
            required
            inputMode="numeric"
            maxLength={6}
            value={form.pincode}
            onChange={(event) => update('pincode', event.target.value.replace(/[^0-9]/g, ''))}
            aria-describedby="addr-pin-hint"
          />
          {form.pincode && !pincodeValid(form.pincode) ? (
            <span id="addr-pin-hint" className="field-hint error">
              Enter a valid 6-digit PIN code
            </span>
          ) : null}
        </label>
      </div>

      <label>
        Delivery instructions <span className="muted small">(optional)</span>
        <textarea
          rows={2}
          maxLength={500}
          value={form.deliveryInstructions}
          onChange={(event) => update('deliveryInstructions', event.target.value)}
        />
      </label>

      <label className="checkbox">
        <input
          type="checkbox"
          checked={form.defaultAddress ?? false}
          onChange={(event) => update('defaultAddress', event.target.checked)}
        />
        Make this my default delivery address
      </label>

      <FieldErrors error={error} />
      {error ? <ErrorNotice error={error} /> : null}

      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Saving…' : 'Save address'}
        </button>
        {onCancel ? (
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}
