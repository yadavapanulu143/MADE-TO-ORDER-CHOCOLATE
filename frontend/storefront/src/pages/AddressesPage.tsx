import { useEffect, useState } from 'react';
import { addressApi } from '../api/endpoints';
import { EmptyState, ErrorNotice, Spinner } from '../components/Feedback';
import { AddressForm } from '../components/AddressForm';
import { useAuth } from '../auth/AuthContext';
import type { Address, AddressInput } from '../api/types';

export function AddressesPage() {
  const { refreshCustomer } = useAuth();
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [editing, setEditing] = useState<Address | null>(null);
  const [creating, setCreating] = useState(false);

  async function load() {
    setAddresses(await addressApi.list());
  }

  useEffect(() => {
    load().catch((err) => setError(err));
  }, []);

  async function create(input: AddressInput) {
    await addressApi.create(input);
    await load();
    setCreating(false);
  }

  async function update(input: AddressInput) {
    if (!editing) return;
    await addressApi.update(editing.id, input);
    await load();
    setEditing(null);
  }

  async function guarded(action: () => Promise<void>) {
    setError(null);
    try {
      await action();
      await refreshCustomer();
    } catch (err) {
      setError(err);
    }
  }

  if (!addresses) return <Spinner label="Loading your addresses…" />;

  return (
    <div className="page narrow">
      <h1>Delivery addresses</h1>
      {error ? <ErrorNotice error={error} /> : null}

      {addresses.length === 0 && !creating ? (
        <EmptyState title="No addresses saved yet">
          <p className="muted">Add an address to make checkout quicker.</p>
        </EmptyState>
      ) : null}

      <ul className="address-list">
        {addresses.map((address) => (
          <li key={address.id} className={address.isDefault ? 'selected' : ''}>
            <div className="address-body">
              <strong>{address.recipientName}</strong>
              {address.isDefault ? <span className="badge badge-info">Default</span> : null}
              <span className="muted block">
                {address.line1}
                {address.line2 ? `, ${address.line2}` : ''}, {address.city}, {address.state} — {address.pincode}
              </span>
              <span className="muted block">{address.mobile}</span>
              {address.deliveryInstructions ? (
                <span className="muted small block">Instructions: {address.deliveryInstructions}</span>
              ) : null}
            </div>
            <div className="address-actions">
              {!address.isDefault ? (
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => guarded(() => addressApi.makeDefault(address.id).then(() => load()))}
                >
                  Make default
                </button>
              ) : null}
              <button type="button" className="btn btn-secondary" onClick={() => setEditing(address)}>
                Edit
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={() =>
                  guarded(() =>
                    addressApi.remove(address.id).then(() => load()),
                  )
                }
              >
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>

      {creating ? (
        <AddressForm onSave={create} onCancel={() => setCreating(false)} />
      ) : editing ? (
        <AddressForm initial={editing} onSave={update} onCancel={() => setEditing(null)} />
      ) : (
        <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
          Add a new address
        </button>
      )}
    </div>
  );
}
