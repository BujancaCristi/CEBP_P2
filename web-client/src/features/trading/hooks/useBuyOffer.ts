import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../integration';
import { tradingApi, tradingKeys, TradingApiError } from '../api/tradingApi';
import type { BuyOfferRequest, BuyOfferResponse } from '../types';

export interface BuyOfferVariables extends BuyOfferRequest {
  orderId: number;
  symbol: string;
}

/**
 * POST /api/offers/{orderId}/buy. On 409 the error's `currentOffer` is what the dialog needs to
 * ask "price changed, confirm?" (PRICE_CHANGED) or to say the offer is gone (NO_LONGER_AVAILABLE).
 */
export function useBuyOffer() {
  const { token } = useAuth();
  const queryClient = useQueryClient();

  return useMutation<BuyOfferResponse, TradingApiError, BuyOfferVariables>({
    mutationFn: ({ orderId, symbol: _symbol, ...req }) => tradingApi.buyOffer(token, orderId, req),
    onSettled: (_data, _error, vars) => {
      // success or conflict, the offers list and the book are stale now
      void queryClient.invalidateQueries({ queryKey: tradingKeys.offers(vars.symbol) });
      void queryClient.invalidateQueries({ queryKey: tradingKeys.book(vars.symbol) });
      void queryClient.invalidateQueries({ queryKey: tradingKeys.myOrders() });
      void queryClient.invalidateQueries({ queryKey: tradingKeys.myTrades() });
    },
  });
}
