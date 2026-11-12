import { apiClient } from './apiClient.js';

export const invoiceService = {
  list: (filters = {}) => {
    const params = new URLSearchParams(
      Object.entries(filters).filter(([, v]) => v !== undefined && v !== null && v !== '')
    );
    const query = params.toString();
    return apiClient(`/invoices${query ? `?${query}` : ''}`);
  },
  get: (id) => apiClient(`/invoices/${id}`),
  create: (invoice) => apiClient('/invoices', { method: 'POST', body: invoice }),
  update: (id, invoice) => apiClient(`/invoices/${id}`, { method: 'PUT', body: invoice }),
  remove: (id) => apiClient(`/invoices/${id}`, { method: 'DELETE' }),
  /** Branded PDF as a Blob (for the Download PDF button). */
  downloadPdf: (id) => apiClient(`/invoices/${id}/pdf`, { binary: true }),
  /** Emails the invoice; resolves with { deliveryStatus, message, ... }. */
  send: (id) => apiClient(`/invoices/${id}/send`, { method: 'POST' }),
};
