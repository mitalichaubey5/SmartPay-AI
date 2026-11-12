import React from 'react';

/**
 * PayPal payment actions for an invoice. In LIVE mode, creating the order
 * opens the sandbox approval page; the buyer returns and clicks Capture.
 * In SIMULATED mode (no sandbox credentials) everything is clearly labelled.
 */
export default function PayPalButton({ invoice, order, busy, onOrder, onCapture }) {
  if (invoice.status === 'PAID') {
    return (
      <div className="paid-banner">
        ✓ Paid{invoice.payment ? ` via ${invoice.payment.method}` : ''}
        {invoice.payment?.paypalCaptureId ? (
          <span className="mono small"> · capture {invoice.payment.paypalCaptureId}</span>
        ) : null}
      </div>
    );
  }

  const live = order?.mode === 'LIVE';
  const simulated = order?.mode === 'SIMULATED';

  return (
    <div className="paypal-panel">
      {!order && (
        <button className="btn btn-paypal" disabled={busy} onClick={() => onOrder(invoice.id)}>
          {busy ? 'Creating order…' : '🅿 Pay with PayPal'}
        </button>
      )}
      {order && (
        <>
          <div className={`mode-note ${simulated ? 'mode-sim' : 'mode-live'}`}>
            {live ? (
              <>
                Order <span className="mono">{order.orderId}</span> created — buyer approval page
                opened in a new tab. After approving, capture the payment below.
              </>
            ) : (
              <>
                <strong>Simulated order</strong> <span className="mono">{order.orderId}</span> —
                this is <strong>not</strong> a real PayPal payment. PayPal Sandbox credentials are
                not configured on the backend: set <span className="mono">PAYPAL_CLIENT_ID</span>{' '}
                and <span className="mono">PAYPAL_CLIENT_SECRET</span> (from
                developer.paypal.com → Sandbox → API credentials) and restart the backend to get
                real checkout links.
              </>
            )}
          </div>
          <div className="paypal-actions">
            {order.approveUrl && (
              <a className="btn btn-ghost" href={order.approveUrl} target="_blank" rel="noopener">
                Open approval page ↗
              </a>
            )}
            <button className="btn btn-paypal" disabled={busy} onClick={() => onCapture(invoice.id)}>
              {busy ? 'Capturing…' : simulated ? 'Simulate capture ✓' : 'Capture payment ✓'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
