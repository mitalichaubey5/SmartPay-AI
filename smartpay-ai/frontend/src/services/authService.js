import { apiClient } from './apiClient.js';

/**
 * Authentication API calls. Successful responses carry
 * { token, expiresIn, user: { id, email, fullName, role } }.
 */
export async function login(email, password) {
  return apiClient('/auth/login', { method: 'POST', body: { email, password } });
}

export async function register(email, password, fullName) {
  return apiClient('/auth/register', { method: 'POST', body: { email, password, fullName } });
}

export function me() {
  return apiClient('/users/me');
}
