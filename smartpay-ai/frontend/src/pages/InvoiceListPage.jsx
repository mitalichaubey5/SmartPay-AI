import React, { useState } from 'react';
import { useInvoices } from '../hooks/useInvoices.js';
import InvoiceTable from '../components/InvoiceTable.jsx';
import { Spinner, ErrorNote } from '../components/Feedback.jsx';

const STATUSES = ['', 'DRAFT', 'SENT', 'PAID', 'OVERDUE'];

export default function InvoiceListPage({ onOpenInvoice }) {
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const { invoices, loading, error } = useInvoices({ status, search });

  return (
    <section>
      <div className="page-head">
        <h1>Invoices</h1>
      </div>
      <div className="filter-bar">
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s || 'All statuses'}
            </option>
          ))}
        </select>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search client, email or invoice number…"
        />
      </div>
      <ErrorNote message={error} />
      {loading ? <Spinner /> : <InvoiceTable invoices={invoices} onOpen={onOpenInvoice} />}
    </section>
  );
}
