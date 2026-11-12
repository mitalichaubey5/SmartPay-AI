import React, { useEffect, useState } from 'react';
import { useInvoices } from '../hooks/useInvoices.js';
import InvoiceTable from '../components/InvoiceTable.jsx';
import { Spinner, EmptyState, ErrorNote } from '../components/Feedback.jsx';

export default function DashboardPage({ onNavigate, onOpenInvoice }) {
  const { invoices, loading, error } = useInvoices();
  const [health, setHealth] = useState(null);

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.json())
      .then(setHealth)
      .catch(() => setHealth(null));
  }, []);

  if (loading) return <Spinner label="Loading dashboard…" />;

  const paid = invoices.filter((i) => i.status === 'PAID');
  const outstanding = invoices.filter((i) => i.status !== 'PAID');
  const outstandingSum = outstanding.reduce((sum, i) => sum + Number(i.total || 0), 0);
  const collectedSum = paid.reduce((sum, i) => sum + Number(i.total || 0), 0);
  const aiGenerated = invoices.filter((i) => i.aiSource).length;

  return (
    <section>
      <div className="page-head">
        <h1>Dashboard</h1>
        <button className="btn btn-primary" onClick={() => onNavigate('generate')}>
          ✨ Generate invoice with AI
        </button>
      </div>

      <ErrorNote message={error} />

      <div className="stat-grid">
        <div className="stat-card">
          <span className="stat-label">Outstanding</span>
          <span className="stat-value">USD {outstandingSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
          <span className="stat-sub">{outstanding.length} unpaid invoice(s)</span>
        </div>
        <div className="stat-card stat-green">
          <span className="stat-label">Collected</span>
          <span className="stat-value">USD {collectedSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
          <span className="stat-sub">{paid.length} paid invoice(s)</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">AI-generated</span>
          <span className="stat-value">{aiGenerated}</span>
          <span className="stat-sub">{invoices.length} invoice(s) total</span>
        </div>
        <div className="stat-card stat-tall">
          <span className="stat-label">System</span>
          <div className="health-lines">
            <div>
              API <span className="dot dot-up" /> UP
            </div>
            <div>
              Database{' '}
              <span className={`dot ${health?.database === 'UP' ? 'dot-up' : 'dot-down'}`} />
              {health ? health.database : '…'}
            </div>
            <div>
              AI engine{' '}
              <span className="ai-tag">{health ? (health.ai.mode === 'LIVE' ? '✦ gemini' : '⚡ local') : '…'}</span>
            </div>
            <div>
              PayPal{' '}
              <span className="ai-tag">{health ? (health.paypal.mode === 'LIVE' ? 'LIVE' : 'SIMULATED') : '…'}</span>
            </div>
          </div>
        </div>
      </div>

      <h2 className="section-title">Recent invoices</h2>
      {invoices.length ? (
        <InvoiceTable invoices={invoices.slice(0, 8)} onOpen={onOpenInvoice} />
      ) : (
        <EmptyState
          icon="🧾"
          title="No invoices yet"
          hint="Describe an invoice in plain English and let the AI draft it for you."
          action={
            <button className="btn btn-primary" onClick={() => onNavigate('generate')}>
              Try the AI Generator
            </button>
          }
        />
      )}
    </section>
  );
}
