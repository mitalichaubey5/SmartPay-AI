import React, { useState } from 'react';
import { useCustomers } from '../hooks/useCustomers.js';
import { customerService } from '../services/customerService.js';
import { useToast } from '../context/ToastContext.jsx';
import { Spinner, ErrorNote, EmptyState } from '../components/Feedback.jsx';

export default function CustomerListPage() {
  const [search, setSearch] = useState('');
  const { customers, loading, error, refresh } = useCustomers(search);
  const { toast } = useToast();
  const [form, setForm] = useState({ name: '', email: '', company: '', city: '', country: '' });
  const [saving, setSaving] = useState(false);

  const addCustomer = async () => {
    if (!form.name || form.name.trim().length < 2) {
      toast('Customer name is required', 'error');
      return;
    }
    setSaving(true);
    try {
      await customerService.create(form);
      toast('Customer added');
      setForm({ name: '', email: '', company: '', city: '', country: '' });
      refresh();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section>
      <div className="page-head">
        <h1>Customers</h1>
      </div>

      <div className="card add-customer">
        <h3>Add customer</h3>
        <div className="form-grid">
          <input
            placeholder="Name *"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <input
            placeholder="Email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <input
            placeholder="Company"
            value={form.company}
            onChange={(e) => setForm({ ...form, company: e.target.value })}
          />
          <input
            placeholder="City"
            value={form.city}
            onChange={(e) => setForm({ ...form, city: e.target.value })}
          />
          <input
            placeholder="Country"
            value={form.country}
            onChange={(e) => setForm({ ...form, country: e.target.value })}
          />
          <button className="btn btn-primary" disabled={saving} onClick={addCustomer}>
            {saving ? 'Saving…' : '+ Add'}
          </button>
        </div>
      </div>

      <div className="filter-bar">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search customers…"
        />
      </div>
      <ErrorNote message={error} />
      {loading ? (
        <Spinner />
      ) : customers.length ? (
        <table className="invoice-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Company</th>
              <th>Location</th>
            </tr>
          </thead>
          <tbody>
            {customers.map((c) => (
              <tr key={c.id}>
                <td className="cell-primary">{c.name}</td>
                <td className="muted">{c.email || '—'}</td>
                <td className="muted">{c.company || '—'}</td>
                <td className="muted">{[c.city, c.country].filter(Boolean).join(', ') || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <EmptyState icon="👥" title="No customers yet" hint="Add the people and companies you bill." />
      )}
    </section>
  );
}
