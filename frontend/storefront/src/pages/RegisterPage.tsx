import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { FieldErrors, ErrorNotice } from '../components/Feedback';
import { mobileValid } from '../lib/format';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', mobile: '', password: '' });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const result = await register(form);
      // Carry the customer id forward so the verify page can send codes for it
      // without the customer typing a uuid.
      navigate('/verify', { state: { customerId: result.customerId, email: result.email, mobile: result.mobile } });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page narrow">
      <h1>Create your account</h1>
      <p className="muted">
        We will email and text you a code to confirm both your email address and your mobile number.
        Ordering opens once both are verified.
      </p>
      <form onSubmit={submit} className="form" noValidate>
        <label>
          Full name
          <input
            type="text"
            autoComplete="name"
            required
            minLength={2}
            maxLength={120}
            value={form.fullName}
            onChange={(event) => update('fullName', event.target.value)}
          />
        </label>
        <label>
          Email address
          <input
            type="email"
            autoComplete="email"
            required
            maxLength={254}
            value={form.email}
            onChange={(event) => update('email', event.target.value)}
          />
        </label>
        <label>
          Mobile number
          <input
            type="tel"
            autoComplete="tel"
            required
            placeholder="10-digit Indian mobile"
            value={form.mobile}
            onChange={(event) => update('mobile', event.target.value)}
            aria-describedby="mobile-hint"
          />
          {form.mobile && !mobileValid(form.mobile) ? (
            <span id="mobile-hint" className="field-hint error">
              Enter a valid 10-digit Indian mobile number
            </span>
          ) : null}
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            maxLength={128}
            value={form.password}
            onChange={(event) => update('password', event.target.value)}
            aria-describedby="password-hint"
          />
          <span id="password-hint" className="field-hint">
            At least 10 characters
          </span>
        </label>

        <FieldErrors error={error} />
        {error ? <ErrorNotice error={error} /> : null}

        <button type="submit" className="btn btn-primary btn-lg" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="muted">
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </div>
  );
}
