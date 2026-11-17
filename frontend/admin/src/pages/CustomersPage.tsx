import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { customerAdminApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { date } from '../lib/format';

const PAGE_SIZE = 20;

export function CustomersPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');

  const load = useCallback(
    () => customerAdminApi.list({ page, size: PAGE_SIZE, search: search || undefined }),
    [page, search],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const rows = data?.content ?? [];

  return (
    <div className="stack">
      <h2>Customers</h2>
      <p className="muted">
        Every account, newest first. Search matches a name, an email or a mobile number. The list
        carries no password, token or verification code — those never leave the identity service.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="customer-search">Search</label>
          <input
            id="customer-search"
            type="search"
            placeholder="Name, email or mobile"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading customers…" /> : null}
      {data && rows.length === 0 && !loading ? (
        <EmptyState title="No customers match" />
      ) : null}

      {rows.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Mobile</th>
                <th>Verified</th>
                <th>Status</th>
                <th>Registered</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((customer) => (
                <tr key={customer.id}>
                  <td>{customer.fullName}</td>
                  <td className="mono">{customer.email}</td>
                  <td className="mono">{customer.mobile}</td>
                  <td>
                    {customer.fullyVerified
                      ? 'Email + mobile'
                      : customer.emailVerified || customer.mobileVerified
                        ? 'Partly verified'
                        : 'Not verified'}
                  </td>
                  <td>
                    <span className={customer.active ? 'badge badge-ok' : 'badge badge-neutral'}>
                      {customer.active ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td>{date(customer.registeredAt)}</td>
                  <td>
                    <Link to={`/customers/${customer.id}`} className="link">Open</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data?.totalElements ?? 0} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}
