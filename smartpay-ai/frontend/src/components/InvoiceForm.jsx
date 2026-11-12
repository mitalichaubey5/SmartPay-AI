import React from 'react';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD', 'CAD', 'SGD'];

/**
 * Editable invoice draft form used by the AI Generator (review before save)
 * — client fields, line items, tax and due date.
 */
export default function InvoiceForm({ value, onChange, customers = [] }) {
  const set = (field, fieldValue) => onChange({ [field]: fieldValue });

  const setItem = (index, field, fieldValue) => {
    const items = value.items.map((it, i) => (i === index ? { ...it, [field]: fieldValue } : it));
    onChange({ items });
  };

  const addItem = () => {
    onChange({ items: [...value.items, { description: '', quantity: 1, unitPrice: 0 }] });
  };

  const removeItem = (index) => {
    onChange({ items: value.items.filter((_, i) => i !== index) });
  };

  const subtotal = (value.items || []).reduce(
    (sum, it) => sum + Number(it.quantity || 0) * Number(it.unitPrice || 0),
    0
  );
  const tax = (subtotal * Number(value.taxRate || 0)) / 100;

  return (
    <div className="invoice-form">
      <div className="form-grid">
        <label>
          Client name
          <input
            value={value.clientName || ''}
            onChange={(e) => set('clientName', e.target.value)}
            placeholder="Acme Corp"
          />
        </label>
        <label>
          Client email
          <input
            value={value.clientEmail || ''}
            onChange={(e) => set('clientEmail', e.target.value)}
            placeholder="billing@acme.com"
          />
        </label>
        <label>
          Customer (optional)
          <select
            value={value.customerId || ''}
            onChange={(e) => set('customerId', e.target.value || null)}
          >
            <option value="">— none —</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Currency
          <select value={value.currency || 'USD'} onChange={(e) => set('currency', e.target.value)}>
            {CURRENCIES.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        <label>
          Tax rate (%)
          <input
            type="number"
            min="0"
            max="100"
            step="0.5"
            value={value.taxRate ?? 0}
            onChange={(e) => set('taxRate', Number(e.target.value))}
          />
        </label>
        <label>
          Due date
          <input
            type="date"
            value={value.dueDate || ''}
            onChange={(e) => set('dueDate', e.target.value)}
          />
        </label>
      </div>

      <div className="items-editor">
        <div className="items-head">
          <h4>Line items</h4>
          <button className="btn btn-ghost" onClick={addItem}>+ Add item</button>
        </div>
        {(value.items || []).map((item, index) => (
          <div className="item-row" key={index}>
            <input
              className="item-desc"
              value={item.description || ''}
              placeholder="Description"
              onChange={(e) => setItem(index, 'description', e.target.value)}
            />
            <input
              className="item-num"
              type="number"
              min="0.01"
              step="0.25"
              value={item.quantity ?? 1}
              onChange={(e) => setItem(index, 'quantity', Number(e.target.value))}
            />
            <span className="item-x">×</span>
            <input
              className="item-num"
              type="number"
              min="0"
              step="0.5"
              value={item.unitPrice ?? 0}
              onChange={(e) => setItem(index, 'unitPrice', Number(e.target.value))}
            />
            <span className="item-amt mono">
              {(Number(item.quantity || 0) * Number(item.unitPrice || 0)).toFixed(2)}
            </span>
            <button
              className="btn btn-danger-ghost"
              title="Remove"
              onClick={() => removeItem(index)}
              disabled={value.items.length === 1}
            >
              ✕
            </button>
          </div>
        ))}
      </div>

      <label className="notes-label">
        Notes
        <textarea
          rows={2}
          value={value.notes || ''}
          onChange={(e) => set('notes', e.target.value)}
          placeholder="Payment terms, thank-you note…"
        />
      </label>

      <div className="totals">
        <div className="totals-row">
          <span>Subtotal</span>
          <span className="mono">{subtotal.toFixed(2)}</span>
        </div>
        <div className="totals-row">
          <span>Tax ({value.taxRate || 0}%)</span>
          <span className="mono">{tax.toFixed(2)}</span>
        </div>
        <div className="totals-row totals-final">
          <span>Total</span>
          <span className="mono">
            {value.currency || 'USD'} {(subtotal + tax).toFixed(2)}
          </span>
        </div>
      </div>
    </div>
  );
}
