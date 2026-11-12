import React from 'react';

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="spinner-wrap">
      <div className="spinner" />
      <span className="muted">{label}</span>
    </div>
  );
}

export function EmptyState({ icon = '🗂️', title, hint, action }) {
  return (
    <div className="empty-state">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      {hint && <p className="muted">{hint}</p>}
      {action}
    </div>
  );
}

export function ErrorNote({ message }) {
  if (!message) return null;
  return <div className="error-note">⚠️ {message}</div>;
}
