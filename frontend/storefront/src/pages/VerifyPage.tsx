import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/endpoints';
import { ErrorNotice } from '../components/Feedback';
import { useAuth } from '../auth/AuthContext';

type Channel = 'EMAIL' | 'MOBILE';

interface VerifyState {
  customerId?: string;
  email?: string;
  mobile?: string;
}

export function VerifyPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { refreshCustomer, signedIn } = useAuth();
  const state = (location.state ?? {}) as VerifyState;

  const [customerId, setCustomerId] = useState(state.customerId ?? '');
  const [channel, setChannel] = useState<Channel>('EMAIL');
  const [code, setCode] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      await authApi.verify(customerId.trim(), channel, code.trim());
      setInfo(`${channel === 'EMAIL' ? 'Email' : 'Mobile number'} verified.`);
      setCode('');
      if (signedIn) {
        // Reflect the new verified flag in the header and unblock ordering.
        await refreshCustomer();
      }
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError(null);
    setInfo(null);
    try {
      await authApi.resend(customerId.trim(), channel);
      setInfo(`A new code is on its way to your ${channel === 'EMAIL' ? 'email' : 'mobile'}.`);
    } catch (err) {
      setError(err);
    }
  }

  return (
    <div className="page narrow">
      <h1>Verify your account</h1>
      <p className="muted">
        Enter the 6-digit code we sent you. Verify both your email and your mobile number to unlock
        ordering.
      </p>

      <form onSubmit={submit} className="form">
        <label>
          Customer id
          <input
            type="text"
            required
            value={customerId}
            onChange={(event) => setCustomerId(event.target.value)}
            placeholder="The id shown after registration"
          />
        </label>

        <fieldset className="channel-picker">
          <legend>Which code is this?</legend>
          <label>
            <input
              type="radio"
              name="channel"
              checked={channel === 'EMAIL'}
              onChange={() => setChannel('EMAIL')}
            />
            Email{state.email ? ` (${state.email})` : ''}
          </label>
          <label>
            <input
              type="radio"
              name="channel"
              checked={channel === 'MOBILE'}
              onChange={() => setChannel('MOBILE')}
            />
            Mobile{state.mobile ? ` (${state.mobile})` : ''}
          </label>
        </fieldset>

        <label>
          Verification code
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            required
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/[^0-9]/g, ''))}
          />
        </label>

        {error ? <ErrorNotice error={error} /> : null}
        {info ? (
          <div className="notice notice-success" role="status">
            {info}
          </div>
        ) : null}

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={busy || code.length !== 6}>
            {busy ? 'Verifying…' : 'Verify'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={resend} disabled={!customerId}>
            Resend code
          </button>
        </div>
      </form>

      <p className="muted">
        {signedIn ? (
          <Link to="/products">Continue shopping</Link>
        ) : (
          <Link to="/login">Sign in</Link>
        )}
      </p>
      <button type="button" className="link" onClick={() => navigate(-1)}>
        Go back
      </button>
    </div>
  );
}
