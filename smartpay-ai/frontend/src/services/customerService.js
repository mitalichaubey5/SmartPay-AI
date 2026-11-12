import { apiClient } from './apiClient.js';

export const customerService = {
  list: (search) => apiClient(`/customers${search ? `?search=${encodeURIComponent(search)}` : ''}`),
  create: (customer) => apiClient('/customers', { method: 'POST', body: customer }),
  update: (id, customer) => apiClient(`/customers/${id}`, { method: 'PUT', body: customer }),
  remove: (id) => apiClient(`/customers/${id}`, { method: 'DELETE' }),
};
