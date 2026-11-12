import { apiClient } from './apiClient.js';

export const paymentService = {
  createPayPalOrder: (invoiceId) =>
    apiClient(`/invoices/${invoiceId}/paypal/order`, { method: 'POST' }),
  capturePayment: (invoiceId) =>
    apiClient(`/invoices/${invoiceId}/paypal/capture`, { method: 'POST' }),
  list: (filters = {}) => {
    const params = new URLSearchParams(
      Object.entries(filters).filter(([, v]) => v !== undefined && v !== null && v !== '')
    );
    const query = params.toString();
    return apiClient(`/payments${query ? `?${query}` : ''}`);
  },
};
