import React from 'react';

export default function StatusBadge({ status }) {
  const map = {
    DRAFT: 'badge-draft',
    SENT: 'badge-sent',
    PAID: 'badge-paid',
    OVERDUE: 'badge-overdue',
    COMPLETED: 'badge-paid',
    CREATED: 'badge-sent',
    FAILED: 'badge-overdue',
    REFUNDED: 'badge-draft',
  };
  return <span className={`badge ${map[status] || 'badge-draft'}`}>{status}</span>;
}
