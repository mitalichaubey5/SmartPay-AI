import React from 'react';
import StatusBadge from './StatusBadge.jsx';

export default function InvoiceTable({ invoices, onOpen, emptyHint }) {
  if (!invoices.length) {
    return (
      <div className="empty-state">
        <div className="empty-icon">🧾</div>
        <h3>No invoices yet</h3>
        <p className="muted">{emptyHint || 'Use the AI Generator to create your first invoice.'}</p>
      </div>
    );
  }
  return (
    <table className="invoice-table">
      <thead>
        <tr>
          <th>Invoice</th>
          <th>Client</th>
          <th className="right">Total</th>
          <th>Due</th>
          <th>Status</th>
          <th>AI</th>
        </tr>
      </thead>
      <tbody>
        {invoices.map((inv) => (
          <tr key={inv.id} className="row-click" onClick={() => onOpen(inv.id)}>
            <td className="mono">{inv.invoiceNumber}</td>
            <td>
              <div className="cell-primary">{inv.clientName}</div>
              <div className="cell-sub muted">{inv.clientEmail || '—'}</div>
            </td>
            <td className="right mono">
              {inv.currency} {Number(inv.total).toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </td>
            <td>{inv.dueDate || '—'}</td>
            <td>
              <StatusBadge status={inv.status} />
            </td>
            <td>
              {inv.aiSource ? (
                <span className={`ai-tag ${inv.aiSource === 'gemini' ? 'ai-gemini' : 'ai-local'}`}>
                  {inv.aiSource === 'gemini' ? '✦ gemini' : '⚡ local'}
                </span>
              ) : (
                <span className="muted">manual</span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
