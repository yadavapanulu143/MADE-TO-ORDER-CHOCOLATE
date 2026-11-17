import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi, orderApi, deliveryApi, productionApi } from '../api/endpoints';
import { useAsync } from '../lib/useAsync';
import { ErrorNotice, Spinner } from '../components/Feedback';
import { MetricCards, Panel } from '../components/Table';

export function DashboardPage() {
  const load = useCallback(async () => {
    const [dashboard, orders, production, deliveries] = await Promise.all([
      dashboardApi.dashboard(),
      orderApi.counters(),
      productionApi.counters(),
      deliveryApi.counters(),
    ]);
    return { dashboard, orders, production, deliveries };
  }, []);

  const { data, error, loading, reload } = useAsync(load, [load]);

  if (loading) return <Spinner label="Loading the dashboard…" />;
  if (error) return <ErrorNotice error={error} onRetry={reload} />;
  if (!data) return null;

  return (
    <div className="stack">
      <h2>Dashboard</h2>

      <MetricCards metrics={data.dashboard.metrics} />

      <div className="cards">
        <Link className="card" to="/orders?status=PAYMENT_PENDING">
          <p className="metric-label">Awaiting payment</p>
          <p className="metric-value">{data.orders.paymentPending}</p>
        </Link>
        <Link className="card" to="/production">
          <p className="metric-label">In the kitchen</p>
          <p className="metric-value">{data.production.inPreparation}</p>
        </Link>
        <Link className="card" to="/production">
          <p className="metric-label">Ready for dispatch</p>
          <p className="metric-value">{data.production.readyForDispatch}</p>
        </Link>
        <Link className="card" to="/deliveries">
          <p className="metric-label">Out for delivery</p>
          <p className="metric-value">{data.deliveries.outForDelivery}</p>
        </Link>
        <Link className="card" to="/orders?status=PAYMENT_FAILED">
          <p className="metric-label">Payment failed</p>
          <p className="metric-value">{data.orders.paymentFailed}</p>
        </Link>
        <Link className="card" to="/deliveries">
          <p className="metric-label">Failed deliveries</p>
          <p className="metric-value">{data.deliveries.attemptFailed}</p>
        </Link>
      </div>

      <div className="detail-grid">
        <Panel title="Recent orders">
          {data.dashboard.recentOrders.length === 0 ? (
            <p className="muted">No orders yet.</p>
          ) : (
            <ul>
              {data.dashboard.recentOrders.map((order) => (
                <li key={order}>{order}</li>
              ))}
            </ul>
          )}
        </Panel>
        <Panel title="Refunds to review">
          {data.dashboard.pendingRefunds.length === 0 ? (
            <p className="muted">Nothing waiting.</p>
          ) : (
            <ul>
              {data.dashboard.pendingRefunds.map((refund) => (
                <li key={refund}>{refund}</li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}
