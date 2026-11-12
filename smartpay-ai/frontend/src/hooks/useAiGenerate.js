import { useCallback, useState } from 'react';
import { aiService } from '../services/aiService.js';

export function useAiGenerate() {
  const [draft, setDraft] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const generate = useCallback(async (prompt) => {
    setLoading(true);
    setError(null);
    setDraft(null);
    try {
      const result = await aiService.generateInvoiceDraft(prompt);
      setDraft(result);
      return result;
    } catch (e) {
      setError(e.message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const updateDraft = useCallback((changes) => {
    setDraft((current) => ({ ...current, ...changes }));
  }, []);

  const reset = useCallback(() => {
    setDraft(null);
    setError(null);
  }, []);

  return { draft, loading, error, generate, updateDraft, reset };
}
