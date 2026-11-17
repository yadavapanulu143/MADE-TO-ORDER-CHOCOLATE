import { useState } from 'react';
import { Link } from 'react-router-dom';
import { customerApi } from '../api/endpoints';
import { FieldErrors, ErrorNotice, Spinner } from '../components/Feedback';
import { formatDate, mobileValid } from '../lib/format';
import { useAuth } from '../auth/AuthContext';

export function ProfilePage() {
  const { customer, refreshCustomer } = useAuth();
  const [fullName, setFullName] = useState(customer?.fullName ?? '');
  const [email, setEmail] = useState(customer?.email ?? '');
  const [mobile, setMobile] = useState(customer?.mobile ?? '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const [profileError, setProfileError] = useState<unknown>(null);
  const [profileInfo, setProfileInfo] = useState<string | null>(null);
  const [contactError, setContactError] = useState<unknown>(null);
  const [contactInfo, setContactInfo] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<unknown>(null);
  const [passwordInfo, setPasswordInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  if (!customer) return <Spinner label="Loading your profile…" />;

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault();
    setProfileError(null);
    setProfileInfo(null);
    setBusy('profile');
    try {
      await customerApi.updateProfile({ fullName });
      await refreshCustomer();
      setProfileInfo('Profile updated.');
    } catch (err) {
      setProfileError(err);
    } finally {
      setBusy(null);
    }
  }

  async function saveContact(event: React.FormEvent) {
    event.preventDefault();
    setContactError(null);
    setContactInfo(null);
    setBusy('contact');
    try {
      const updated = await customerApi.changeContact({ email, mobile });
      await refreshCustomer();
      // A contact change clears that channel's verified flag on the server, so say
      // so plainly rather than leaving the customer to discover ordering is blocked.
      setContactInfo(
        `Contact details updated.${!updated.emailVerified ? ' Please re-verify your email.' : ''}${
          !updated.mobileVerified ? ' Please re-verify your mobile number.' : ''
        }`,
      );
    } catch (err) {
      setContactError(err);
    } finally {
      setBusy(null);
    }
  }

  async function savePassword(event: React.FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    setPasswordInfo(null);
    setBusy('password');
    try {
      await customerApi.changePassword({ currentPassword, newPassword });
      setPasswordInfo('Password changed.');
      setCurrentPassword('');
      setNewPassword('');
    } catch (err) {
      setPasswordError(err);
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="page narrow">
      <h1>Your profile</h1>

      <section className="panel">
        <h2>Account</h2>
        <dl className="account-meta">
          <div>
            <dt>Name</dt>
            <dd>{customer.fullName}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>
              {customer.email}{' '}
              {customer.emailVerified ? (
                <span className="badge badge-success">Verified</span>
              ) : (
                <span className="badge badge-warn">Unverified</span>
              )}
            </dd>
          </div>
          <div>
            <dt>Mobile</dt>
            <dd>
              {customer.mobile}{' '}
              {customer.mobileVerified ? (
                <span className="badge badge-success">Verified</span>
              ) : (
                <span className="badge badge-warn">Unverified</span>
              )}
            </dd>
          </div>
          <div>
            <dt>Member since</dt>
            <dd>{formatDate(customer.registeredAt)}</dd>
          </div>
        </dl>
        {!customer.fullyVerified ? (
          <div className="notice notice-warn">
            <p>Verify both your email and mobile number to place orders.</p>
            <Link to="/verify" state={{ customerId: customer.id, email: customer.email, mobile: customer.mobile }} className="btn btn-secondary">
              Verify now
            </Link>
          </div>
        ) : null}
      </section>

      <section className="panel">
        <h2>Name</h2>
        <form onSubmit={saveProfile} className="form">
          <label>
            Full name
            <input
              type="text"
              required
              minLength={2}
              maxLength={120}
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
            />
          </label>
          <FieldErrors error={profileError} />
          {profileError ? <ErrorNotice error={profileError} /> : null}
          {profileInfo ? (
            <div className="notice notice-success" role="status">
              {profileInfo}
            </div>
          ) : null}
          <button type="submit" className="btn btn-primary" disabled={busy === 'profile'}>
            Save name
          </button>
        </form>
      </section>

      <section className="panel">
        <h2>Contact details</h2>
        <p className="muted small">
          Changing your email or mobile number un-verifies that channel until you confirm the new one.
        </p>
        <form onSubmit={saveContact} className="form">
          <label>
            Email address
            <input
              type="email"
              required
              maxLength={254}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Mobile number
            <input
              type="tel"
              required
              value={mobile}
              onChange={(event) => setMobile(event.target.value)}
            />
            {mobile && !mobileValid(mobile) ? (
              <span className="field-hint error">Enter a valid 10-digit Indian mobile number</span>
            ) : null}
          </label>
          <FieldErrors error={contactError} />
          {contactError ? <ErrorNotice error={contactError} /> : null}
          {contactInfo ? (
            <div className="notice notice-success" role="status">
              {contactInfo}
            </div>
          ) : null}
          <button type="submit" className="btn btn-primary" disabled={busy === 'contact'}>
            Save contact details
          </button>
        </form>
      </section>

      <section className="panel">
        <h2>Password</h2>
        <form onSubmit={savePassword} className="form">
          <label>
            Current password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>
          <label>
            New password
            <input
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
              maxLength={128}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <span className="field-hint">At least 12 characters</span>
          </label>
          <FieldErrors error={passwordError} />
          {passwordError ? <ErrorNotice error={passwordError} /> : null}
          {passwordInfo ? (
            <div className="notice notice-success" role="status">
              {passwordInfo}
            </div>
          ) : null}
          <button type="submit" className="btn btn-primary" disabled={busy === 'password'}>
            Change password
          </button>
        </form>
      </section>

      <p>
        <Link to="/addresses" className="btn btn-secondary">
          Manage delivery addresses
        </Link>
      </p>
    </div>
  );
}
