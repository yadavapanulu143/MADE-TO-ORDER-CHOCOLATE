import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/endpoints';
import { ErrorNotice } from '../components/Feedback';

// Two stages in one page: ask for a reset email, then complete it with the token
// from that email. The token can also arrive as a ?token= link the customer opens
// directly, which skips straight to the completion form.
export function PasswordResetPage() {
  const [params] = useSearchParams();
  const [stage, setStage] = useState<'request' | 'complete'>(params.get('token') ? 'complete' : 'request');
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(params.get('token') ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function requestReset(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      await authApi.requestPasswordReset(email);
      setInfo('If that email is registered, a reset link is on its way. Check your inbox.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function completeReset(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      await authApi.completePasswordReset(token, newPassword);
      setInfo('Your password has been reset. You can now sign in.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page narrow">
      <h1>Reset your password</h1>

      {stage === 'request' ? (
        <form onSubmit={requestReset} className="form">
          <label>
            Email address
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          {error ? <ErrorNotice error={error} /> : null}
          {info ? (
            <div className="notice notice-success" role="status">
              {info}
            </div>
          ) : null}
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Sending…' : 'Send reset link'}
          </button>
          <button type="button" className="link" onClick={() => setStage('complete')}>
            I already have a reset token
          </button>
        </form>
      ) : (
        <form onSubmit={completeReset} className="form">
          <label>
            Reset token
            <input
              type="text"
              required
              value={token}
              onChange={(event) => setToken(event.target.value)}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={10}
              maxLength={128}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <span className="field-hint">At least 10 characters</span>
          </label>
          {error ? <ErrorNotice error={error} /> : null}
          {info ? (
            <div className="notice notice-success" role="status">
              {info}
            </div>
          ) : null}
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Resetting…' : 'Reset password'}
          </button>
        </form>
      )}

      <p className="muted">
        <Link to="/login">Back to sign in</Link>
      </p>
    </div>
  );
}
