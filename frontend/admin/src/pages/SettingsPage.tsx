import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { settingsApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, FieldErrors, Spinner, SuccessNotice } from '../components/Feedback';
import { Panel } from '../components/Table';
import type { Settings } from '../api/types';

export function SettingsPage() {
  const load = useCallback(() => settingsApi.get(), []);
  const { data, error, loading, reload } = useAsync(load, [load]);

  const [form, setForm] = useState<Settings | null>(null);
  const [saveError, setSaveError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  function field(key: keyof Settings, value: string) {
    setForm((current) => (current ? { ...current, [key]: value } : current));
    setSaved(false);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!form) return;
    setBusy(true);
    setSaveError(null);
    setSaved(false);
    try {
      await settingsApi.update(form);
      setSaved(true);
      reload();
    } catch (caught) {
      setSaveError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner label="Loading settings…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!form) return null;

  return (
    <div className="stack">
      <h2>Application settings</h2>
      <p className="muted">
        The seller's own details and the policies shown on the storefront. Nothing here is a secret;
        credentials live only in the environment.
      </p>

      {saveError ? <ErrorNotice error={saveError} /> : null}
      <FieldErrors error={saveError} />
      {saved ? <SuccessNotice>Settings saved.</SuccessNotice> : null}

      <form className="form" onSubmit={save}>
        <Panel title="Seller details">
          <div className="field">
            <label htmlFor="sellerName">Seller name</label>
            <input id="sellerName" value={form.sellerName ?? ''} onChange={(e) => field('sellerName', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="contactEmail">Contact email</label>
            <input id="contactEmail" type="email" value={form.contactEmail ?? ''} onChange={(e) => field('contactEmail', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="contactPhone">Contact phone</label>
            <input id="contactPhone" value={form.contactPhone ?? ''} onChange={(e) => field('contactPhone', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="contactAddress">Contact address</label>
            <textarea id="contactAddress" rows={2} value={form.contactAddress ?? ''} onChange={(e) => field('contactAddress', e.target.value)} />
          </div>
        </Panel>

        <Panel title="Published policies">
          <div className="field">
            <label htmlFor="privacyPolicy">Privacy policy</label>
            <textarea id="privacyPolicy" rows={4} value={form.privacyPolicy ?? ''} onChange={(e) => field('privacyPolicy', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="termsAndConditions">Terms and conditions</label>
            <textarea id="termsAndConditions" rows={4} value={form.termsAndConditions ?? ''} onChange={(e) => field('termsAndConditions', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="shippingPolicy">Shipping policy</label>
            <textarea id="shippingPolicy" rows={4} value={form.shippingPolicy ?? ''} onChange={(e) => field('shippingPolicy', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="refundPolicy">Refund policy</label>
            <textarea id="refundPolicy" rows={4} value={form.refundPolicy ?? ''} onChange={(e) => field('refundPolicy', e.target.value)} />
          </div>
        </Panel>
        <button type="submit" className="btn btn-primary btn-lg" disabled={busy}>
          {busy ? 'Saving…' : 'Save settings'}
        </button>
      </form>
    </div>
  );
}
