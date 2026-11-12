import React, { useState } from 'react';
import { useAiGenerate } from '../hooks/useAiGenerate.js';
import { useCustomers } from '../hooks/useCustomers.js';
import { useToast } from '../context/ToastContext.jsx';
import InvoiceForm from '../components/InvoiceForm.jsx';
import { invoiceService } from '../services/invoiceService.js';
import { ErrorNote } from '../components/Feedback.jsx';

const SAMPLES = [
  'Invoice Acme Corp for 10 hours of web development at $75/hour, 18% tax, due in 14 days, contact billing@acmecorp.com',
  'Create an invoice for Jane Smith for 15 hours of consulting at $100 per hour with 10% tax due in 30 days',
  'Bill Global Tech $500 for website maintenance plus 5 units of logo design at $200 each, 8% tax, due in 7 days',
];

export default function InvoiceGeneratorPage({ onSaved, onNavigate }) {
  const { draft, loading, error, generate, updateDraft, reset } = useAiGenerate();
  const { customers } = useCustomers();
  const { toast } = useToast();
  const [prompt, setPrompt] = useState('');
  const [saving, setSaving] = useState(false);

  const runGenerate = async () => {
    if (prompt.trim().length < 10) {
      toast('Prompt needs at least 10 characters', 'error');
      return;
    }
    const result = await generate(prompt);
    if (result) {
      toast(
        result.source === 'gemini'
          ? 'Draft generated with Gemini ✦'
          : 'Draft generated with the local parser ⚡'
      );
    }
  };

  const saveInvoice = async () => {
    setSaving(true);
    try {
      const payload = {
        clientName: draft.clientName || 'New Client',
        clientEmail: draft.clientEmail || null,
        customerId: draft.customerId ? Number(draft.customerId) : null,
        currency: draft.currency || 'USD',
        taxRate: Number(draft.taxRate || 0),
        dueDate: draft.dueDate,
        notes: draft.notes || null,
        aiSource: draft.source,
        items: draft.items.map((it) => ({
          description: it.description,
          quantity: Number(it.quantity),
          unitPrice: Number(it.unitPrice),
        })),
      };
      const created = await invoiceService.create(payload);
      toast(`Invoice ${created.invoiceNumber} created`);
      reset();
      setPrompt('');
      onSaved(created.id);
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section>
      <div className="page-head">
        <h1>✨ AI Invoice Generator</h1>
      </div>
      <p className="muted lead">
        Describe the invoice in plain English — SmartPay AI structures it into an editable draft.
      </p>

      <div className="prompt-card">
        <textarea
          rows={3}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="e.g. Invoice Acme Corp for 10 hours of web development at $75/hour, 18% tax, due in 14 days"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) runGenerate();
          }}
        />
        <div className="prompt-actions">
          <button className="btn btn-primary" disabled={loading} onClick={runGenerate}>
            {loading ? 'Thinking…' : 'Generate draft'}
          </button>
          <span className="muted small">Ctrl+Enter</span>
        </div>
        <div className="samples">
          <span className="muted small">Try:</span>
          {SAMPLES.map((s, i) => (
            <button key={i} className="chip" onClick={() => setPrompt(s)}>
              {s.length > 52 ? s.slice(0, 52) + '…' : s}
            </button>
          ))}
        </div>
      </div>

      <ErrorNote message={error} />

      {draft && (
        <div className="draft-card">
          <div className="draft-head">
            <h2>Review &amp; edit draft</h2>
            <span className={`ai-tag ${draft.source === 'gemini' ? 'ai-gemini' : 'ai-local'}`}>
              {draft.source === 'gemini' ? '✦ gemini' : '⚡ local parser'}
            </span>
          </div>
          <p className="muted">{draft.summary}</p>
          <InvoiceForm value={draft} onChange={updateDraft} customers={customers} />
          <div className="draft-actions">
            <button className="btn btn-primary" disabled={saving} onClick={saveInvoice}>
              {saving ? 'Saving…' : '💾 Create invoice'}
            </button>
            <button className="btn btn-ghost" onClick={reset}>
              Discard
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
