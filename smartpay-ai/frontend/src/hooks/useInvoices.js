import { useCallback, useEffect, useState } from 'react';
import { invoiceService } from '../services/invoiceService.js';

export function useInvoices(filters = {}) {
  const [invoices, setInvoices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setInvoices(await invoiceService.list(filters));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [JSON.stringify(filters)]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { invoices, loading, error, refresh };
}
