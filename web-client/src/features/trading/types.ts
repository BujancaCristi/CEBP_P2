/** TypeScript mirror of contracts/s1/trading.yaml (+ the event payloads pushed over STOMP). */

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'LIMIT' | 'MARKET';
export type OrderStatus = 'PENDING' | 'OPEN' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED';
export type OrderSource = 'WEB' | 'MARKET_MAKER';

export interface PlaceOrderRequest {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  /** required for LIMIT, omitted for MARKET */
  price?: number;
  qty: number;
}

export interface ModifyOrderRequest {
  price?: number;
  qty?: number;
}

export interface BuyOfferRequest {
  qty: number;
  maxPrice: number;
  seenVersion: number;
}

export interface Order {
  id: number;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: number | null;
  qty: number;
  remainingQty: number;
  filledQty: number;
  status: OrderStatus;
  rejectReason: string | null;
  source: OrderSource;
  createdTime: string;
  updatedAt: string;
  version: number;
}

export interface Offer {
  orderId: number;
  symbol: string;
  sellerUserId: number;
  price: number;
  remainingQty: number;
  totalPrice: number;
  /** send back as seenVersion when buying */
  version: number;
  status: OrderStatus;
  createdTime: string;
}

export interface Trade {
  id: number;
  symbol: string;
  buyOrderId: number;
  sellOrderId: number;
  buyerUserId: number;
  sellerUserId: number;
  qty: number;
  price: number;
  totalValue: number;
  executedAt: string;
  side?: 'BUY' | 'SELL' | null;
}

export interface BuyOfferResponse {
  trade: Trade;
  order: Order;
  offer: Offer;
}

export interface BookLevel {
  price: number;
  qty: number;
  orders: number;
}

export interface Book {
  symbol: string;
  lastPrice: number;
  bestBid: number | null;
  bestAsk: number | null;
  /** highest first */
  bids: BookLevel[];
  /** lowest first */
  asks: BookLevel[];
  asOf: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type ProblemCode =
  | 'VALIDATION'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INVALID_STATE'
  | 'PRICE_CHANGED'
  | 'NO_LONGER_AVAILABLE'
  | 'INSUFFICIENT_FUNDS'
  | 'INSUFFICIENT_SHARES'
  | 'UNAUTHORIZED'
  | 'INTERNAL';

/** RFC 9457 body of every 4xx/5xx from the trading API. */
export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  code: ProblemCode | string;
  errors?: Record<string, string>;
  /** buy-from-offer 409 only */
  reason?: 'PRICE_CHANGED' | 'NO_LONGER_AVAILABLE';
  /** buy-from-offer 409 only: the offer as it is now (null if it no longer exists) */
  offer?: Offer | null;
}

// ---- STOMP payloads (contracts/events/schemas) ----

export interface TradeExecutedEvent {
  tradeId: number;
  symbol: string;
  buyOrderId: number;
  sellOrderId: number;
  buyerUserId: number;
  sellerUserId: number;
  qty: number;
  price: number;
  totalValue: number;
  executedAt: string;
}

export interface PriceUpdatedEvent {
  symbol: string;
  value: number;
  previousClose: number;
}

export interface OrderEvent {
  orderId: number;
  userId: number;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: number | null;
  qty: number;
  remainingQty: number;
  status: OrderStatus;
  source: OrderSource;
  reason: string | null;
  version: number;
  updatedAt: string;
}
