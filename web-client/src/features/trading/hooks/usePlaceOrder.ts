import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../integration';
import { tradingApi, tradingKeys, TradingApiError } from '../api/tradingApi';
import type { Order, PlaceOrderRequest } from '../types';

/**
 * POST /api/orders. Resolves with the PENDING order (202) or the REJECTED order (422 - no cash
 * or shares); rejects with TradingApiError for everything else (400 validation, 403, 404…).
 * Follow the order's life on /user/queue/orders (useMyOrderUpdates).
 */
export function usePlaceOrder() {
  const { token } = useAuth();
  const queryClient = useQueryClient();

  return useMutation<Order, TradingApiError, PlaceOrderRequest>({
    mutationFn: (req) => tradingApi.placeOrder(token, req),
    onSuccess: (order) => {
      void queryClient.invalidateQueries({ queryKey: tradingKeys.myOrders() });
      void queryClient.invalidateQueries({ queryKey: tradingKeys.offers(order.symbol) });
    },
  });
}
