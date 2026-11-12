import { useCallback, useState } from 'react';
import { paymentService } from '../services/paymentService.js';

/**
 * PayPal payment flow for one invoice:
 * create order -> (buyer approves via link in LIVE mode) -> capture.
 */
export function usePayment() {
  const [order, setOrder] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const createOrder = useCallback(async (invoiceId) => {
    setBusy(true);
    setError(null);
    try {
      const result = await paymentService.createPayPalOrder(invoiceId);
      setOrder(result);
      if (result.approveUrl) {
        window.open(result.approveUrl, '_blank', 'noopener');
      }
      return result;
    } catch (e) {
      setError(e.message);
      return null;
    } finally {
      setBusy(false);
    }
  }, []);

  const capture = useCallback(async (invoiceId) => {
    setBusy(true);
    setError(null);
    try {
      return await paymentService.capturePayment(invoiceId);
    } catch (e) {
      setError(e.message);
      return null;
    } finally {
      setBusy(false);
    }
  }, []);

  return { order, busy, error, createOrder, capture };
}
