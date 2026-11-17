import { useCallback, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { catalogAdminApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { StatusBadge } from '../components/StatusBadge';
import { money } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

const PAGE_SIZE = 20;

export function ProductsPage() {
  const { can } = useAdminAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const load = useCallback(
    () => catalogAdminApi.list({ page, size: PAGE_SIZE, search: search || undefined, status: status ? [status] : [] }),
    [page, search, status],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [formError, setFormError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.PRODUCT_WRITE);

  async function create(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFormError(null);
    try {
      await catalogAdminApi.create({ name, shortDescription: shortDescription || undefined });
      setName('');
      setShortDescription('');
      setCreating(false);
      reload();
    } catch (caught) {
      setFormError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <h2>Products</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="prod-search">Search</label>
          <input
            id="prod-search"
            type="search"
            placeholder="Name or slug"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="prod-status">Status</label>
          <select id="prod-status" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
        {writable ? (
          <button type="button" className="btn btn-primary" onClick={() => setCreating((v) => !v)}>
            {creating ? 'Close' : 'New product'}
          </button>
        ) : null}
      </div>

      {creating ? (
        <section className="panel">
          <h3>New product</h3>
          <form className="form" onSubmit={create}>
            <div className="field">
              <label htmlFor="new-name">Name</label>
              <input id="new-name" required value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="new-short">Short description</label>
              <input
                id="new-short"
                value={shortDescription}
                onChange={(event) => setShortDescription(event.target.value)}
              />
            </div>
            <FieldErrors error={formError} />
            {formError ? <ErrorNotice error={formError} /> : null}
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Creating…' : 'Create draft'}
            </button>
          </form>
        </section>
      ) : null}

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading products…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No products yet" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Slug</th>
                <th>From price</th>
                <th>Variants</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((product) => (
                <tr key={product.id}>
                  <td>{product.name}</td>
                  <td className="mono">{product.slug}</td>
                  <td>{money(product.fromPriceMinorUnits, product.fromPrice, product.currency)}</td>
                  <td>{product.variantCount}</td>
                  <td><StatusBadge status={product.status} /></td>
                  <td><Link to={`/products/${product.id}`} className="link">Open</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data.totalElements} onPage={setPage} />
        </>
      ) : null}
    </div>
  );
}
