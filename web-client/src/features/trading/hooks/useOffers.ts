import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../integration';
import { tradingApi, tradingKeys } from '../api/tradingApi';

/**
 * Offers (open SELL orders) of a stock, best price first. Invalidated by useOrderBook whenever
 * the book changes, so it stays live without its own subscription. A slow poll is the fallback
 * when the WebSocket is down.
 */
export function useOffers(symbol: string) {
  const { token } = useAuth();
  return useQuery({
    queryKey: tradingKeys.offers(symbol),
    queryFn: () => tradingApi.offers(token, symbol),
    enabled: !!symbol,
    refetchInterval: 15_000,
  });
}
