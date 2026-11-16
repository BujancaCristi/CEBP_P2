import { useCallback, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth, useStomp } from '../integration';
import { tradingApi, tradingKeys } from '../api/tradingApi';
import type { Book, PriceUpdatedEvent, Trade, TradeExecutedEvent } from '../types';

const TAPE_LENGTH = 20;

/**
 * The order book of a stock: fetched once, then replaced by every push on /topic/book/{symbol}.
 * Also keeps the last price (/topic/prices) and a short tape of recent trades (/topic/trades).
 */
export function useOrderBook(symbol: string, depth = 10) {
  const { token } = useAuth();
  const queryClient = useQueryClient();
  const key = tradingKeys.book(symbol);

  const book = useQuery({
    queryKey: [...key, depth],
    queryFn: () => tradingApi.book(token, symbol, depth),
    staleTime: 60_000, // live pushes keep it fresh; refetch only on remount
    enabled: !!symbol,
  });

  const tape = useQuery({
    queryKey: tradingKeys.trades(symbol),
    queryFn: () => tradingApi.recentTrades(token, symbol, TAPE_LENGTH),
    staleTime: 60_000,
    enabled: !!symbol,
  });

  const [lastPrice, setLastPrice] = useState<number | null>(null);

  useStomp<Book>(
    `/topic/book/${symbol}`,
    useCallback(
      (fresh: Book) => {
        queryClient.setQueryData<Book>([...key, depth], fresh);
        // the book changed, so the offers list did too
        void queryClient.invalidateQueries({ queryKey: tradingKeys.offers(symbol) });
      },
      [queryClient, symbol, depth, key],
    ),
    !!symbol,
  );

  useStomp<PriceUpdatedEvent>(
    `/topic/prices/${symbol}`,
    useCallback((p: PriceUpdatedEvent) => setLastPrice(p.value), []),
    !!symbol,
  );

  useStomp<TradeExecutedEvent>(
    `/topic/trades/${symbol}`,
    useCallback(
      (t: TradeExecutedEvent) => {
        setLastPrice(t.price);
        const trade: Trade = {
          id: t.tradeId,
          symbol: t.symbol,
          buyOrderId: t.buyOrderId,
          sellOrderId: t.sellOrderId,
          buyerUserId: t.buyerUserId,
          sellerUserId: t.sellerUserId,
          qty: t.qty,
          price: t.price,
          totalValue: t.totalValue,
          executedAt: t.executedAt,
        };
        queryClient.setQueryData<Trade[]>(tradingKeys.trades(symbol), (old) =>
          [trade, ...(old ?? []).filter((x) => x.id !== trade.id)].slice(0, TAPE_LENGTH),
        );
      },
      [queryClient, symbol],
    ),
    !!symbol,
  );

  return {
    book: book.data,
    trades: tape.data ?? [],
    lastPrice: lastPrice ?? book.data?.lastPrice ?? null,
    isLoading: book.isLoading,
    error: book.error,
    refetch: book.refetch,
  };
}
