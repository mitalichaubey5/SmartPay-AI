const BASE = '/api';

/**
 * Fetch wrapper for the SmartPay REST API. Parses the backend error
 * envelope and throws Error(message) so callers can surface clean text.
 * Pass binary: true to receive a Blob (e.g. invoice PDF downloads).
 */
export async function apiClient(path, { method = 'GET', body, binary = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('smartpay_token');
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) return null;

  if (binary && response.ok) {
    return response.blob();
  }

  let payload = null;
  const text = await response.text();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { message: text };
    }
  }

  if (!response.ok) {
    // An expired or revoked token: drop the session so the app returns
    // to the login screen instead of looping on failing requests.
    if (response.status === 401) {
      localStorage.removeItem('smartpay_token');
      localStorage.removeItem('smartpay_user');
      window.dispatchEvent(new Event('smartpay:unauthorized'));
    }
    const message = payload?.message || `Request failed (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
  return payload;
}
