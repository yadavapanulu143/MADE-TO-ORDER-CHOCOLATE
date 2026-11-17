import { useSettings } from '../lib/useSettings';
import { Spinner } from '../components/Feedback';

export function PoliciesPage() {
  const settings = useSettings();

  return (
    <div className="page narrow">
      <h1>Policies</h1>
      {!settings.updatedAt ? <Spinner label="Loading policies…" /> : null}

      {settings.shippingPolicy ? (
        <section className="prose">
          <h2>Shipping</h2>
          <p>{settings.shippingPolicy}</p>
        </section>
      ) : null}
      {settings.refundPolicy ? (
        <section className="prose">
          <h2>Refunds and cancellations</h2>
          <p>{settings.refundPolicy}</p>
        </section>
      ) : null}
      {settings.termsAndConditions ? (
        <section className="prose">
          <h2>Terms and conditions</h2>
          <p>{settings.termsAndConditions}</p>
        </section>
      ) : null}
      {settings.privacyPolicy ? (
        <section className="prose">
          <h2>Privacy</h2>
          <p>{settings.privacyPolicy}</p>
        </section>
      ) : null}

      {settings.updatedAt && !settings.shippingPolicy && !settings.refundPolicy && !settings.termsAndConditions && !settings.privacyPolicy ? (
        <p className="muted">The seller has not published policies yet.</p>
      ) : null}
    </div>
  );
}
