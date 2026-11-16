import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth, useStomp } from '../integration';
import { tradingKeys } from '../api/tradingApi';
import type { OrderEvent } from '../types';

/**
 * Follows the caller's own orders on /user/queue/orders. Returns the latest event per order id
 * (so the order form can show "PENDING → FILLED" for what it just placed) and invalidates the
 * cached "my orders" list on every change.
 */
export function useMyOrderUpdates(symbol?: string) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [latest, setLatest] = useState<Record<number, OrderEvent>>({});

  useStomp<OrderEvent>(
    '/user/queue/orders',
    useCallback(
      (e: OrderEvent) => {
        if (symbol && e.symbol !== symbol) return;
        setLatest((prev) => ({ ...prev, [e.orderId]: e }));
        void queryClient.invalidateQueries({ queryKey: tradingKeys.myOrders() });
      },
      [queryClient, symbol],
    ),
    !!user,
  );

  return latest;
}
