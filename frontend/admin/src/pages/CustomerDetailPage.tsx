import { useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { customerAdminApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { Field, Panel } from '../components/Table';
import { dateTime } from '../lib/format';

export function CustomerDetailPage() {
  const { customerId = '' } = useParams();
  const load = useCallback(() => customerAdminApi.get(customerId), [customerId]);
  const { data, error, loading, reload } = useAsync(load, [load]);

  if (loading) return <Spinner label="Loading the customer…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <h2>{data.fullName}</h2>

      <div className="detail-grid">
        <Panel title="Account">
          <Field label="Email" value={data.email} mono />
          <Field label="Mobile" value={data.mobile} mono />
          <Field
            label="Email verified"
            value={data.emailVerified ? 'Yes' : 'No'}
          />
          <Field
            label="Mobile verified"
            value={data.mobileVerified ? 'Yes' : 'No'}
          />
          <Field label="Fully verified" value={data.fullyVerified ? 'Yes' : 'No'} />
          <Field label="Status" value={data.active ? 'Active' : 'Disabled'} />
          <Field label="Registered" value={dateTime(data.registeredAt)} />
        </Panel>

        <Panel title="Delivery addresses">
          {data.addresses.length === 0 ? (
            <p className="muted">This customer has no saved addresses.</p>
          ) : (
            <ul className="stack">
              {data.addresses.map((address) => (
                <li key={address.id}>
                  <strong>{address.recipientName}</strong>
                  {address.isDefault ? ' · default' : ''}
                  <br />
                  <span className="muted">
                    {address.line1}
                    {address.line2 ? `, ${address.line2}` : ''}, {address.city}, {address.state}{' '}
                    {address.pincode}
                  </span>
                  <br />
                  <span className="mono small">{address.mobile}</span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}
