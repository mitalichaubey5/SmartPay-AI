import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import * as authService from '../services/authService.js';

const TOKEN_KEY = 'smartpay_token';
const USER_KEY = 'smartpay_user';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Restore the last session from localStorage; the token itself is
  // re-validated against /users/me as soon as the app mounts.
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
    } catch {
      return null;
    }
  });
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  // Validate the persisted token once on mount.
  useEffect(() => {
    let cancelled = false;
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      setReady(true);
      return undefined;
    }
    authService
      .me()
      .then((fresh) => {
        if (!cancelled && fresh) {
          localStorage.setItem(USER_KEY, JSON.stringify(fresh));
          setUser(fresh);
        }
      })
      .catch(() => {
        if (!cancelled) logout();
      })
      .finally(() => {
        if (!cancelled) setReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, [logout]);

  // If any request later answers 401 (expired token), drop the session.
  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener('smartpay:unauthorized', onUnauthorized);
    return () => window.removeEventListener('smartpay:unauthorized', onUnauthorized);
  }, [logout]);

  const applySession = useCallback((tokenResponse) => {
    localStorage.setItem(TOKEN_KEY, tokenResponse.token);
    localStorage.setItem(USER_KEY, JSON.stringify(tokenResponse.user));
    setUser(tokenResponse.user);
    return tokenResponse.user;
  }, []);

  const login = useCallback(
    async (email, password) => applySession(await authService.login(email, password)),
    [applySession]
  );

  const register = useCallback(
    async (email, password, fullName) =>
      applySession(await authService.register(email, password, fullName)),
    [applySession]
  );

  return (
    <AuthContext.Provider value={{ user, ready, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
