import React, { useState } from 'react';
import Navbar from './components/Navbar.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import InvoiceGeneratorPage from './pages/InvoiceGeneratorPage.jsx';
import InvoiceListPage from './pages/InvoiceListPage.jsx';
import InvoiceDetailPage from './pages/InvoiceDetailPage.jsx';
import CustomerListPage from './pages/CustomerListPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import { useAuth } from './context/AuthContext.jsx';

export default function App() {
  const { user, ready, logout } = useAuth();
  const [tab, setTab] = useState('dashboard');
  const [selectedInvoiceId, setSelectedInvoiceId] = useState(null);

  const openInvoice = (id) => {
    setSelectedInvoiceId(id);
    setTab('invoice');
  };

  const handleLogout = () => {
    logout();
    setTab('dashboard');
    setSelectedInvoiceId(null);
  };

  // Session restored from localStorage is being re-validated.
  if (!ready) {
    return (
      <div className="app">
        <div className="splash">
          <span className="logo">⚡</span> SmartPay AI — loading your session…
        </div>
      </div>
    );
  }

  // Unauthenticated users only ever see the login screen.
  if (!user) {
    return <LoginPage />;
  }

  return (
    <div className="app">
      <Navbar tab={tab} onNavigate={setTab} user={user} onLogout={handleLogout} />
      <main className="page">
        {tab === 'dashboard' && <DashboardPage onNavigate={setTab} onOpenInvoice={openInvoice} />}
        {tab === 'generate' && (
          <InvoiceGeneratorPage onSaved={openInvoice} onNavigate={setTab} />
        )}
        {tab === 'invoices' && <InvoiceListPage onOpenInvoice={openInvoice} />}
        {tab === 'invoice' && (
          <InvoiceDetailPage invoiceId={selectedInvoiceId} onNavigate={setTab} />
        )}
        {tab === 'customers' && <CustomerListPage />}
      </main>
      <footer className="footer">
        SmartPay AI · AI-generated invoices · PayPal Sandbox payments
      </footer>
    </div>
  );
}
