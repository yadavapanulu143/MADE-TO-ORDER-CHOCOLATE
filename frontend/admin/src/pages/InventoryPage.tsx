import { useCallback, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { inventoryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { dateTime } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';

const PAGE_SIZE = 20;
const UNITS = ['GRAM', 'KILOGRAM', 'MILLILITRE', 'LITRE', 'PIECE', 'PACKET'];

export function InventoryPage() {
  const { can } = useAdminAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [includeInactive, setIncludeInactive] = useState(false);

  const load = useCallback(
    () => inventoryApi.list({ page, size: PAGE_SIZE, search: search || undefined, includeInactive }),
    [page, search, includeInactive],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [name, setName] = useState('');
  const [unit, setUnit] = useState('GRAM');
  const [openingLevel, setOpeningLevel] = useState('');
  const [minimumLevel, setMinimumLevel] = useState('');
  const [createError, setCreateError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.SETTINGS_WRITE);

  async function create(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setCreateError(null);
    try {
      await inventoryApi.create({
        name,
        unit,
        openingLevel: Number(openingLevel),
        minimumLevel: Number(minimumLevel),
      });
      setName('');
      setOpeningLevel('');
      setMinimumLevel('');
      reload();
    } catch (caught) {
      setCreateError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <h2>Ingredients and packaging</h2>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="ing-search">Search</label>
          <input
            id="ing-search"
            type="search"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="ing-inactive">
            <input
              id="ing-inactive"
              type="checkbox"
              checked={includeInactive}
              onChange={(event) => { setIncludeInactive(event.target.checked); setPage(0); }}
            />{' '}
            Include retired items
          </label>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading ingredients…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No ingredients tracked yet" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>On hand</th>
                <th>Minimum</th>
                <th>Unit</th>
                <th>Low stock</th>
                <th>Updated</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((ingredient) => (
                <tr key={ingredient.id}>
                  <td>{ingredient.name}</td>
                  <td>{ingredient.onHand}</td>
                  <td>{ingredient.minimumLevel}</td>
                  <td>{ingredient.unit}</td>
                  <td>{ingredient.lowStock ? 'Yes' : 'No'}</td>
                  <td>{dateTime(ingredient.updatedAt)}</td>
                  <td><Link to={`/inventory/${ingredient.id}`} className="link">Open</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data.totalElements} onPage={setPage} />
        </>
      ) : null}

      {writable ? (
        <section className="panel">
          <h3>Add an ingredient</h3>
          <form className="form" onSubmit={create}>
            <div className="field">
              <label htmlFor="ing-name">Name</label>
              <input id="ing-name" required value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="ing-unit">Unit</label>
              <select id="ing-unit" value={unit} onChange={(e) => setUnit(e.target.value)}>
                {UNITS.map((value) => <option key={value} value={value}>{value}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="ing-opening">Opening level</label>
              <input
                id="ing-opening"
                inputMode="decimal"
                required
                value={openingLevel}
                onChange={(e) => setOpeningLevel(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="ing-min">Minimum level</label>
              <input
                id="ing-min"
                inputMode="decimal"
                required
                value={minimumLevel}
                onChange={(e) => setMinimumLevel(e.target.value)}
              />
            </div>
            <FieldErrors error={createError} />
            {createError ? <ErrorNotice error={createError} /> : null}
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Adding…' : 'Add ingredient'}
            </button>
          </form>
        </section>
      ) : null}
    </div>
  );
}
