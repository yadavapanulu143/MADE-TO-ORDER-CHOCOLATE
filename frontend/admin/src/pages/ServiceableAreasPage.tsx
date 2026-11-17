import { useCallback, useState, type FormEvent } from 'react';
import { deliveryApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { EmptyState, ErrorNotice, FieldErrors, Spinner } from '../components/Feedback';
import { Pager } from '../components/Table';
import { money, toMinorUnits, toRupeeInput } from '../lib/format';
import { useAdminAuth } from '../auth/AdminAuthContext';
import { Permissions } from '../auth/permissions';
import type { ServiceableArea } from '../api/types';

const PAGE_SIZE = 20;

export function ServiceableAreasPage() {
  const { can } = useAdminAuth();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [includeInactive, setIncludeInactive] = useState(false);

  const load = useCallback(
    () => deliveryApi.areas({ page, size: PAGE_SIZE, search: search || undefined, includeInactive }),
    [page, search, includeInactive],
  );
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [editing, setEditing] = useState<ServiceableArea | null>(null);
  const [prefix, setPrefix] = useState('');
  const [areaName, setAreaName] = useState('');
  const [charge, setCharge] = useState('');
  const [active, setActive] = useState(true);
  const [formError, setFormError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const writable = can(Permissions.SETTINGS_WRITE);

  function resetForm() {
    setEditing(null);
    setPrefix('');
    setAreaName('');
    setCharge('');
    setActive(true);
    setFormError(null);
  }

  function startEdit(area: ServiceableArea) {
    setEditing(area);
    setPrefix(area.pincodePrefix);
    setAreaName(area.areaName ?? '');
    setCharge(toRupeeInput(area.deliveryChargeMinorUnits));
    setActive(area.active);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setFormError(null);
    const body = {
      pincodePrefix: prefix,
      areaName,
      deliveryChargeMinorUnits: toMinorUnits(charge),
      active,
    };
    try {
      if (editing) {
        await deliveryApi.updateArea(editing.id, body);
      } else {
        await deliveryApi.createArea(body);
      }
      resetForm();
      reload();
    } catch (caught) {
      setFormError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <h2>Delivery areas</h2>
      <p className="muted">
        A PIN code or a leading prefix of one. The longest matching prefix wins, so a specific PIN
        can be priced differently from the region around it. A zero charge is a real, free area.
      </p>

      <div className="toolbar">
        <div className="field">
          <label htmlFor="area-search">Search</label>
          <input
            id="area-search"
            type="search"
            placeholder="PIN or area name"
            defaultValue={search}
            onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          />
        </div>
        <div className="field">
          <label htmlFor="area-inactive">
            <input
              id="area-inactive"
              type="checkbox"
              checked={includeInactive}
              onChange={(event) => { setIncludeInactive(event.target.checked); setPage(0); }}
            />{' '}
            Include inactive
          </label>
        </div>
      </div>

      {error ? <ErrorNotice error={error} onRetry={reload} /> : null}
      {loading ? <Spinner label="Loading delivery areas…" /> : null}
      {data && data.content.length === 0 && !loading ? <EmptyState title="No delivery areas configured" /> : null}

      {data && data.content.length > 0 ? (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>PIN / prefix</th>
                <th>Area</th>
                <th>Charge</th>
                <th>Active</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.content.map((area) => (
                <tr key={area.id}>
                  <td className="mono">{area.pincodePrefix}</td>
                  <td>{area.areaName ?? '—'}</td>
                  <td>{money(area.deliveryChargeMinorUnits, area.deliveryCharge, area.currency)}</td>
                  <td>{area.active ? 'Yes' : 'No'}</td>
                  <td>
                    {writable ? (
                      <button type="button" className="btn btn-ghost" onClick={() => startEdit(area)}>
                        Edit
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={page} size={PAGE_SIZE} totalElements={data.totalElements} onPage={setPage} />
        </>
      ) : null}

      {writable ? (
        <section className="panel">
          <h3>{editing ? `Edit ${editing.pincodePrefix}` : 'Add a delivery area'}</h3>
          <form className="form" onSubmit={submit}>
            <div className="field">
              <label htmlFor="prefix">PIN code or prefix</label>
              <input
                id="prefix"
                required
                pattern="[1-9][0-9]{0,5}"
                title="1 to 6 digits, not starting with 0"
                value={prefix}
                onChange={(event) => setPrefix(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="area-name">Area name</label>
              <input id="area-name" required value={areaName} onChange={(event) => setAreaName(event.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="charge">Delivery charge (₹)</label>
              <input
                id="charge"
                inputMode="decimal"
                required
                value={charge}
                onChange={(event) => setCharge(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="area-active">
                <input
                  id="area-active"
                  type="checkbox"
                  checked={active}
                  onChange={(event) => setActive(event.target.checked)}
                />{' '}
                Active
              </label>
            </div>
            <FieldErrors error={formError} />
            {formError ? <ErrorNotice error={formError} /> : null}
            <div className="row">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {editing ? 'Save changes' : 'Add area'}
              </button>
              {editing ? (
                <button type="button" className="btn btn-ghost" onClick={resetForm}>
                  Cancel
                </button>
              ) : null}
            </div>
          </form>
        </section>
      ) : null}
    </div>
  );
}
