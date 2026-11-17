import { useState } from 'react';
import { catalogApi } from '../api/endpoints';
import { ErrorNotice } from '../components/Feedback';
import { pincodeValid } from '../lib/format';
import type { Serviceability } from '../api/types';

export function ServiceabilityPage() {
  const [pincode, setPincode] = useState('');
  const [result, setResult] = useState<Serviceability | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function check(event: React.FormEvent) {
    event.preventDefault();
    if (!pincodeValid(pincode)) return;
    setError(null);
    setResult(null);
    setBusy(true);
    try {
      setResult(await catalogApi.serviceability(pincode.trim()));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page narrow">
      <h1>Delivery areas</h1>
      <p className="muted">
        We deliver ourselves to a set of PIN codes. Enter your PIN code to see whether we reach you and
        what delivery costs.
      </p>

      <form onSubmit={check} className="form">
        <label>
          PIN code
          <input
            type="text"
            inputMode="numeric"
            maxLength={6}
            required
            value={pincode}
            onChange={(event) => setPincode(event.target.value.replace(/[^0-9]/g, ''))}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy || !pincodeValid(pincode)}>
          {busy ? 'Checking…' : 'Check'}
        </button>
      </form>

      {error ? <ErrorNotice error={error} /> : null}

      {result ? (
        result.serviceable ? (
          <div className="notice notice-success" role="status">
            <p>
              Good news — we deliver to {result.pincode}. Delivery charge:{' '}
              {result.deliveryChargeMinorUnits === 0 ? 'free' : result.deliveryCharge}.
            </p>
          </div>
        ) : (
          <div className="notice notice-warn" role="status">
            <p>{result.message}</p>
          </div>
        )
      ) : null}
    </div>
  );
}
