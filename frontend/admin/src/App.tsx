import { Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { CustomersPage } from './pages/CustomersPage';
import { CustomerDetailPage } from './pages/CustomerDetailPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { PaymentsPage } from './pages/PaymentsPage';
import { PaymentDetailPage } from './pages/PaymentDetailPage';
import { InvoicesPage } from './pages/InvoicesPage';
import { InvoiceDetailPage } from './pages/InvoiceDetailPage';
import { ProductionPage } from './pages/ProductionPage';
import { ProductionJobPage } from './pages/ProductionJobPage';
import { InventoryPage } from './pages/InventoryPage';
import { IngredientDetailPage } from './pages/IngredientDetailPage';
import { DeliveriesPage } from './pages/DeliveriesPage';
import { DeliveryDetailPage } from './pages/DeliveryDetailPage';
import { ServiceableAreasPage } from './pages/ServiceableAreasPage';
import { ProductsPage } from './pages/ProductsPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { NotificationDetailPage } from './pages/NotificationDetailPage';
import { ReportsPage } from './pages/ReportsPage';
import { ExportsPage } from './pages/ExportsPage';
import { SettingsPage } from './pages/SettingsPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="customers" element={<CustomersPage />} />
          <Route path="customers/:customerId" element={<CustomerDetailPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:orderId" element={<OrderDetailPage />} />
          <Route path="payments" element={<PaymentsPage />} />
          <Route path="payments/:paymentId" element={<PaymentDetailPage />} />
          <Route path="invoices" element={<InvoicesPage />} />
          <Route path="invoices/:invoiceId" element={<InvoiceDetailPage />} />
          <Route path="production" element={<ProductionPage />} />
          <Route path="production/:jobId" element={<ProductionJobPage />} />
          <Route path="inventory" element={<InventoryPage />} />
          <Route path="inventory/:ingredientId" element={<IngredientDetailPage />} />
          <Route path="deliveries" element={<DeliveriesPage />} />
          <Route path="deliveries/:orderId" element={<DeliveryDetailPage />} />
          <Route path="serviceable-areas" element={<ServiceableAreasPage />} />
          <Route path="products" element={<ProductsPage />} />
          <Route path="products/:productId" element={<ProductDetailPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="notifications/:id" element={<NotificationDetailPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="exports" element={<ExportsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
