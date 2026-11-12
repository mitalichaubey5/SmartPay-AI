import { apiClient } from './apiClient.js';

export const aiService = {
  generateInvoiceDraft: (prompt) =>
    apiClient('/ai/generate-invoice', { method: 'POST', body: { prompt } }),
};
