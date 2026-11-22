import { S1_BASE_URL } from '../integration';
import { MOCK_DATA } from '@/app/mock';
import { mockBook, mockOffers, mockTrades, mockMyOrders } from '../mock';
import type {
  Book,
  BuyOfferRequest,
  BuyOfferResponse,
  ModifyOrderRequest,
  Offer,
  Order,
  Page,
  PlaceOrderRequest,
  Problem,
  Trade,
} from '../types';

/** Thrown for every non-2xx answer; carries the parsed problem details. */
export class TradingApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'TradingApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code;
  }

  /** 409 from buy-from-offer: the offer as it is now. */
  get currentOffer(): Offer | null | undefined {
    return this.problem.offer;
  }
}

export function isTradingApiError(e: unknown): e is TradingApiError {
  return e instanceof TradingApiError;
}

async function request<T>(token: string | null, method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(`${S1_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (res.ok) {
    return (res.status === 204 ? undefined : await res.json()) as T;
  }

  let problem: Problem;
  try {
    problem = (await res.json()) as Problem;
  } catch {
    problem = { type: 'about:blank', title: res.statusText, status: res.status, code: 'INTERNAL' };
  }
  throw new TradingApiError(res.status, problem);
}

function isRejectedOrder(body: unknown): body is Order {
  return typeof body === 'object' && body !== null && (body as Order).status === 'REJECTED' && typeof (body as Order).id === 'number';
}

/** All calls of contracts/s1/trading.yaml. `token` comes from Cezar's useAuth(). */
export const tradingApi = {
  book: (token: string | null, symbol: string, depth = 10) =>
    MOCK_DATA ? Promise.resolve(mockBook(symbol, depth))
      : request<Book>(token, 'GET', `/api/stocks/${encodeURIComponent(symbol)}/book?depth=${depth}`),

  offers: (token: string | null, symbol: string) =>
    MOCK_DATA ? Promise.resolve(mockOffers(symbol))
      : request<Offer[]>(token, 'GET', `/api/offers?symbol=${encodeURIComponent(symbol)}`),

  recentTrades: (token: string | null, symbol: string, limit = 20) =>
    MOCK_DATA ? Promise.resolve(mockTrades(symbol).slice(0, limit))
      : request<Trade[]>(token, 'GET', `/api/trades?symbol=${encodeURIComponent(symbol)}&limit=${limit}`),

  /**
   * 202 → the PENDING order. 422 → the order was stored as REJECTED and the body IS that order
   * (not a problem document), so it is returned as a normal result with status REJECTED.
   */
  placeOrder: async (token: string | null, req: PlaceOrderRequest): Promise<Order> => {
    try {
      return await request<Order>(token, 'POST', '/api/orders', req);
    } catch (e) {
      if (e instanceof TradingApiError && e.status === 422 && isRejectedOrder(e.problem)) {
        return e.problem;
      }
      throw e;
    }
  },

  modifyOrder: (token: string | null, id: number, req: ModifyOrderRequest) =>
    request<Order>(token, 'PATCH', `/api/orders/${id}`, req),

  cancelOrder: (token: string | null, id: number) => request<Order>(token, 'DELETE', `/api/orders/${id}`),

  getOrder: (token: string | null, id: number) => request<Order>(token, 'GET', `/api/orders/${id}`),

  myOrders: (token: string | null, page = 0, size = 50) =>
    MOCK_DATA ? Promise.resolve({
      content: mockMyOrders().slice(page * size, (page + 1) * size),
      page, size, totalElements: mockMyOrders().length, totalPages: 1,
    }) : request<Page<Order>>(token, 'GET', `/api/orders/mine?page=${page}&size=${size}`),

  myTrades: (token: string | null, page = 0, size = 50) =>
    MOCK_DATA ? Promise.resolve({
      content: mockTrades('ACME').slice(page * size, (page + 1) * size),
      page, size, totalElements: 12, totalPages: 1,
    }) : request<Page<Trade>>(token, 'GET', `/api/trades/mine?page=${page}&size=${size}`),

  buyOffer: (token: string | null, orderId: number, req: BuyOfferRequest) =>
    request<BuyOfferResponse>(token, 'POST', `/api/offers/${orderId}/buy`, req),
};

/** Query keys, exported so other features (e.g. Cezar's My orders) can invalidate them. */
export const tradingKeys = {
  book: (symbol: string) => ['trading', 'book', symbol] as const,
  offers: (symbol: string) => ['trading', 'offers', symbol] as const,
  trades: (symbol: string) => ['trading', 'trades', symbol] as const,
  myOrders: () => ['trading', 'orders', 'mine'] as const,
  myTrades: () => ['trading', 'trades', 'mine'] as const,
};
