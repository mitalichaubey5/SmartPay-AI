import React from 'react';

const TABS = [
  { id: 'dashboard', label: 'Dashboard', icon: '📊' },
  { id: 'generate', label: 'AI Generator', icon: '✨' },
  { id: 'invoices', label: 'Invoices', icon: '🧾' },
  { id: 'customers', label: 'Customers', icon: '👥' },
];

export default function Navbar({ tab, onNavigate, user, onLogout }) {
  return (
    <header className="navbar">
      <div className="navbar-brand" onClick={() => onNavigate('dashboard')}>
        <span className="logo">⚡</span>
        <span>
          SmartPay <strong>AI</strong>
        </span>
      </div>
      <nav className="navbar-tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            className={`tab ${tab === t.id ? 'tab-active' : ''}`}
            onClick={() => onNavigate(t.id)}
          >
            <span className="tab-icon">{t.icon}</span> {t.label}
          </button>
        ))}
      </nav>
      {user && (
        <div className="navbar-user">
          <span className="user-chip" title={user.email}>
            👤 {user.fullName || user.email}
          </span>
          <button type="button" className="btn btn-ghost btn-logout" onClick={onLogout}>
            Sign out
          </button>
        </div>
      )}
    </header>
  );
}
