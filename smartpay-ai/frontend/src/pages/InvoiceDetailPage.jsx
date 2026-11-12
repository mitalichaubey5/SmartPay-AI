import React, { useCallback, useEffect, useState } from 'react';
import { invoiceService } from '../services/invoiceService.js';
import { paymentService } from '../services/paymentService.js';
import { usePayment } from '../hooks/usePayment.js';
import { useCustomers } from '../hooks/useCustomers.js';
import { useToast } from '../context/ToastContext.jsx';
import PayPalButton from '../components/PayPalButton.jsx';
import InvoiceForm from '../components/InvoiceForm.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import { Spinner, ErrorNote } from '../components/Feedback.jsx';

export default function InvoiceDetailPage({ invoiceId, onNavigate }) {
  const [invoice, setInvoice] = useState(null);
  const [payments, setPayments] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const { order, busy, error: payError, createOrder, capture } = usePayment();
  const { toast } = useToast();
  const { customers } = useCustomers();

  // ---- edit-in-place (drafts only; the backend re-computes totals on save)
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(null);
  const [saving, setSaving] = useState(false);

  // ---- email sending + pdf + print
  const [sending, setSending] = useState(false);
  const [sendResult, setSendResult] = useState(null);
  const [pdfBusy, setPdfBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      const [inv, pays] = await Promise.all([
        invoiceService.get(invoiceId),
        paymentService.list({ invoiceId }),
      ]);
      setInvoice(inv);
      setPayments(pays);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [invoiceId]);

  useEffect(() => {
    if (invoiceId) reload();
  }, [reload]);

  // ------------------------------------------------------------ edit invoice

  const startEdit = () => {
    setDraft({
      clientName: invoice.clientName || '',
      clientEmail: invoice.clientEmail || '',
      customerId: invoice.customerId ?? '',
      currency: invoice.currency || 'USD',
      taxRate: Number(invoice.taxRate ?? 0),
      dueDate: invoice.dueDate || '',
      notes: invoice.notes || '',
      aiSource: invoice.aiSource || null,
      items: (invoice.items || []).map((it) => ({
        description: it.description,
        quantity: Number(it.quantity),
        unitPrice: Number(it.unitPrice),
      })),
    });
    setEditing(true);
    setSendResult(null);
  };

  const patchDraft = (patch) => setDraft((current) => ({ ...current, ...patch }));

  const saveEdit = async () => {
    if (saving) return;
    if (!draft.clientName || !draft.clientName.trim()) {
      toast('Client name is required', 'error');
      return;
    }
    if (draft.items.some((it) => !it.description || !it.description.trim())) {
      toast('Every line item needs a description', 'error');
      return;
    }
    setSaving(true);
    try {
      const body = {
        clientName: draft.clientName.trim(),
        clientEmail: draft.clientEmail?.trim() || null,
        customerId: draft.customerId ? Number(draft.customerId) : null,
        currency: draft.currency,
        taxRate: Number(draft.taxRate) || 0,
        dueDate: draft.dueDate || null,
        notes: draft.notes?.trim() || null,
        aiSource: draft.aiSource || null,
        items: draft.items.map((it) => ({
          description: it.description.trim(),
          quantity: Number(it.quantity) || 0,
          unitPrice: Number(it.unitPrice) || 0,
        })),
      };
      const updated = await invoiceService.update(invoice.id, body);
      toast(`Saved — totals recomputed server-side: ${updated.currency} ${Number(updated.total).toFixed(2)}`);
      setEditing(false);
      setDraft(null);
      await reload();
    } catch (e) {
      toast(e.message || 'Could not save the invoice', 'error');
    } finally {
      setSaving(false);
    }
  };

  // ------------------------------------------------------------ send invoice

  const handleSend = async () => {
    if (sending) return;
    setSending(true);
    setSendResult(null);
    try {
      const result = await invoiceService.send(invoice.id);
      setSendResult(result);
      if (result.deliveryStatus === 'SENT') {
        toast('Invoice emailed with PDF attached 📧');
      } else if (result.deliveryStatus === 'FAILED') {
        toast('Email delivery failed', 'error');
      } else {
        toast('Email is not configured on the server', 'error');
      }
      await reload();
    } catch (e) {
      setSendResult({ deliveryStatus: 'FAILED', message: e.message, recipient: null });
      toast(e.message, 'error');
    } finally {
      setSending(false);
    }
  };

  // --------------------------------------------------------- download / print

  const handleDownloadPdf = async () => {
    if (pdfBusy) return;
    setPdfBusy(true);
    try {
      const blob = await invoiceService.downloadPdf(invoice.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${invoice.invoiceNumber}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast('Invoice PDF downloaded');
    } catch (e) {
      toast(e.message || 'Could not generate the PDF', 'error');
    } finally {
      setPdfBusy(false);
    }
  };

  const handlePrint = () => window.print();

  // ---------------------------------------------------------------- paypal

  const handleOrder = async (id) => {
    const result = await createOrder(id);
    if (result) {
      toast(
        result.mode === 'LIVE'
          ? 'PayPal order created — approval page opened'
          : 'Simulated order created (no sandbox credentials configured)'
      );
    }
  };

  const handleCapture = async (id) => {
    const paid = await capture(id);
    if (paid) {
      toast(`Payment captured — ${paid.invoiceNumber} is PAID 🎉`);
      await reload();
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Delete invoice ${invoice.invoiceNumber}?`)) return;
    try {
      await invoiceService.remove(invoice.id);
      toast('Invoice deleted');
      onNavigate('invoices');
    } catch (e) {
      toast(e.message, 'error');
    }
  };

  // ------------------------------------------------------------------ render

  if (loading) return <Spinner />;
  if (error) return <ErrorNote message={error} />;
  if (!invoice) return null;

  const isDraft = invoice.status === 'DRAFT';

  return (
    <section>
      <div className="page-head no-print">
        <div>
          <h1 className="mono">{invoice.invoiceNumber}</h1>
          <span className="muted">
            <StatusBadge status={invoice.status} />
            {invoice.aiSource && (
              <span className={`ai-tag ${invoice.aiSource === 'gemini' ? 'ai-gemini' : 'ai-local'}`}>
                {invoice.aiSource === 'gemini' ? '✦ gemini' : '⚡ local'}
              </span>
            )}
          </span>
        </div>
        <div className="head-actions">
          {isDraft && (
            <button className="btn btn-primary" onClick={startEdit}>
              ✏️ Edit invoice
            </button>
          )}
          <button className="btn btn-ghost" onClick={handleSend} disabled={sending}>
            {sending ? 'Sending…' : '📧 Send invoice'}
          </button>
          <button className="btn btn-ghost" onClick={handleDownloadPdf} disabled={pdfBusy}>
            {pdfBusy ? 'Preparing…' : '⬇️ Download PDF'}
          </button>
          <button className="btn btn-ghost" onClick={handlePrint}>
            🖨️ Print
          </button>
          {isDraft && (
            <button className="btn btn-danger-ghost" onClick={handleDelete}>
              Delete draft
            </button>
          )}
        </div>
      </div>

      {sendResult && (
        <div className={`email-result email-${(sendResult.deliveryStatus || '').toLowerCase()} no-print`}>
          <div className="email-status-row">
            <span className="email-chip">{sendResult.deliveryStatus}</span>
            {sendResult.recipient && (
              <span className="muted small">to {sendResult.recipient}</span>
            )}
          </div>
          <p>{sendResult.message}</p>
          {sendResult.paypalLinkIncluded && (
            <p className="muted small">
              A genuine PayPal Sandbox checkout link was included in the email.
            </p>
          )}
          {sendResult.paypalMode === 'NOT_CONFIGURED' && (
            <p className="muted small">
              To include a PayPal payment link, set PAYPAL_CLIENT_ID and
              PAYPAL_CLIENT_SECRET (sandbox credentials) on the backend.
            </p>
          )}
        </div>
      )}

      {editing ? (
        <div className="card edit-card no-print">
          <div className="items-head">
            <h3>Edit draft invoice</h3>
            <span className="muted small">
              Only DRAFT invoices are editable; totals are recalculated on the server.
            </span>
          </div>
          <InvoiceForm value={draft} onChange={patchDraft} customers={customers} />
          <div className="head-actions">
            <button className="btn btn-primary" onClick={saveEdit} disabled={saving}>
              {saving ? 'Saving…' : 'Save changes'}
            </button>
            <button
              className="btn btn-ghost"
              onClick={() => {
                setEditing(false);
                setDraft(null);
              }}
              disabled={saving}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="detail-grid">
          <div className="card">
            <h3>Client</h3>
            <div className="cell-primary">{invoice.clientName}</div>
            <div className="muted">{invoice.clientEmail || 'no email'}</div>
            {invoice.customerName && <div className="muted small">customer: {invoice.customerName}</div>}
            <h3 className="mt">Due date</h3>
            <div>{invoice.dueDate || '—'}</div>
            {invoice.notes && (
              <>
                <h3 className="mt">Notes</h3>
                <p className="muted">{invoice.notes}</p>
              </>
            )}
          </div>

          <div className="card">
            <h3>Line items</h3>
            <table className="invoice-table">
              <thead>
                <tr>
                  <th>Description</th>
                  <th className="right">Qty</th>
                  <th className="right">Price</th>
                  <th className="right">Amount</th>
                </tr>
              </thead>
              <tbody>
                {invoice.items.map((it) => (
                  <tr key={it.id}>
                    <td>{it.description}</td>
                    <td className="right mono">{Number(it.quantity)}</td>
                    <td className="right mono">{Number(it.unitPrice).toFixed(2)}</td>
                    <td className="right mono">{Number(it.amount).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="totals">
              <div className="totals-row">
                <span>Subtotal</span>
                <span className="mono">{invoice.currency} {Number(invoice.subtotal).toFixed(2)}</span>
              </div>
              <div className="totals-row">
                <span>Tax ({Number(invoice.taxRate)}%)</span>
                <span className="mono">{invoice.currency} {Number(invoice.taxAmount).toFixed(2)}</span>
              </div>
              <div className="totals-row totals-final">
                <span>Total</span>
                <span className="mono">{invoice.currency} {Number(invoice.total).toFixed(2)}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="card pay-card no-print">
        <h3>Payment</h3>
        <PayPalButton
          invoice={invoice}
          order={order}
          busy={busy}
          onOrder={handleOrder}
          onCapture={handleCapture}
        />
        <ErrorNote message={payError} />
        {payments.length > 0 && (
          <div className="mt">
            <h4>Payment attempts</h4>
            <table className="invoice-table">
              <thead>
                <tr>
                  <th>Method</th>
                  <th>Status</th>
                  <th>Order</th>
                  <th className="right">Amount</th>
                  <th>Captured</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td>{p.method}</td>
                    <td>
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="mono small">{p.paypalOrderId}</td>
                    <td className="right mono">{p.currency} {Number(p.amount).toFixed(2)}</td>
                    <td className="small">{p.capturedAt ? p.capturedAt.replace('T', ' ').slice(0, 19) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
