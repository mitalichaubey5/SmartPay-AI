import { useCallback, useEffect, useState } from 'react';
import { customerService } from '../services/customerService.js';

export function useCustomers(search = '') {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCustomers(await customerService.list(search));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { customers, loading, error, refresh };
}
