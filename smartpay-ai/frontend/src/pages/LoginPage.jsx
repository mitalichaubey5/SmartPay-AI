import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';

const DEMO_EMAIL = 'demo@smartpay.test';
const DEMO_PASSWORD = 'Demo!SmartPay1';
const DEMO_NAME = 'Demo User';

/**
 * Login / registration screen. Also offers a one-click demo account
 * (created on first use, then signed into afterwards).
 */
export default function LoginPage() {
  const { login, register } = useAuth();
  const { toast } = useToast();
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const resetMessages = () => {
    setError('');
  };

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    resetMessages();
    setBusy(true);
    try {
      const user =
        mode === 'login'
          ? await login(email.trim(), password)
          : await register(email.trim(), password, fullName.trim() || null);
      toast(`Welcome, ${user.fullName || user.email}!`, 'success');
    } catch (err) {
      setError(err.message || 'Authentication failed');
    } finally {
      setBusy(false);
    }
  };

  const demoLogin = async () => {
    if (busy) return;
    resetMessages();
    setBusy(true);
    try {
      // First click creates the account, later clicks just sign in.
      let user;
      try {
        user = await login(DEMO_EMAIL, DEMO_PASSWORD);
      } catch {
        user = await register(DEMO_EMAIL, DEMO_PASSWORD, DEMO_NAME);
      }
      toast(`Welcome, ${user.fullName || user.email}!`, 'success');
    } catch (err) {
      setError(err.message || 'Could not start the demo session');
    } finally {
      setBusy(false);
    }
  };

  const switchMode = (next) => {
    setMode(next);
    resetMessages();
  };

  return (
    <div className="login-wrap">
      <div className="card login-card">
        <div className="login-brand">
          <span className="logo">⚡</span>
          <span>
            SmartPay <strong>AI</strong>
          </span>
        </div>
        <p className="login-tagline">
          AI-generated invoices · PayPal payments · your data, your account.
        </p>

        <form onSubmit={submit} className="login-form">
          {mode === 'register' && (
            <label>
              Full name
              <input
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Jane Freelancer"
                autoComplete="name"
                maxLength={150}
              />
            </label>
          )}
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              maxLength={150}
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'register' ? 'At least 8 characters' : 'Your password'}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              minLength={8}
              maxLength={100}
              required
            />
          </label>

          {error && <div className="login-error">{error}</div>}

          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <div className="login-alt">
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
          >
            {mode === 'login' ? 'New here? Create an account' : 'Already registered? Sign in'}
          </button>
          <button type="button" className="btn btn-ghost" onClick={demoLogin} disabled={busy}>
            ⚡ Try the demo account
          </button>
        </div>
      </div>
    </div>
  );
}
