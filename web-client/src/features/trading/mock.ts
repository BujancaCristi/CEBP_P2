import type { Book, Offer, Order, Trade } from './types';
import { mockStocks } from '@/app/mock';

export function mockBook(symbol: string, depth = 10): Book {
  const stock = mockStocks.find(s => s.symbol === symbol) ?? mockStocks[0];
  const last = stock.value;
  return {
    symbol,
    lastPrice: last,
    bestBid: last - 0.12,
    bestAsk: last + 0.10,
    bids: Array.from({ length: depth }, (_, i) => ({
      price: Number((last - 0.12 - i * 0.05).toFixed(2)),
      qty: 100 + i * 35,
      orders: 1 + (i % 4),
    })),
    asks: Array.from({ length: depth }, (_, i) => ({
      price: Number((last + 0.10 + i * 0.05).toFixed(2)),
      qty: 80 + i * 40,
      orders: 1 + (i % 3),
    })),
    asOf: new Date().toISOString(),
  };
}

export function mockOffers(symbol: string): Offer[] {
  const last = mockBook(symbol).lastPrice;
  return [0, 1, 2].map((i) => ({
    orderId: 1000 + i,
    symbol,
    sellerUserId: 900 + i,
    price: Number((last + 0.10 + i * 0.05).toFixed(2)),
    remainingQty: 250 - i * 50,
    totalPrice: Number(((last + 0.10 + i * 0.05) * (250 - i * 50)).toFixed(2)),
    version: i + 1,
    status: 'OPEN',
    createdTime: new Date(Date.now() - i * 60_000).toISOString(),
  }));
}

export function mockTrades(symbol: string): Trade[] {
  const last = mockBook(symbol).lastPrice;
  return Array.from({ length: 12 }, (_, i) => ({
    id: 5000 - i,
    symbol,
    buyOrderId: 600 + i,
    sellOrderId: 700 + i,
    buyerUserId: 12,
    sellerUserId: 20,
    qty: 25 + (i % 5) * 20,
    price: Number((last - i * 0.03).toFixed(2)),
    totalValue: 0,
    executedAt: new Date(Date.now() - i * 45_000).toISOString(),
  })).map(t => ({ ...t, totalValue: Number((t.qty * t.price).toFixed(2)) }));
}

export function mockMyOrders(symbol?: string): Order[] {
  const symbols = symbol ? [symbol] : mockStocks.map(s => s.symbol);
  return symbols.flatMap((s, stockIndex) => [0, 1].map((i) => {
    const stock = mockStocks.find(x => x.symbol === s) ?? mockStocks[0];
    const status: Order['status'] = i === 0 ? 'OPEN' : 'PARTIALLY_FILLED';
    const qty = 100 + stockIndex * 25;
    const remaining = i === 0 ? qty : 40;
    return {
      id: 2000 + stockIndex * 10 + i,
      symbol: s,
      side: i === 0 ? 'BUY' : 'SELL',
      type: 'LIMIT',
      price: Number((stock.value - (i === 0 ? 1 : -1)).toFixed(2)),
      qty,
      remainingQty: remaining,
      filledQty: qty - remaining,
      status,
      rejectReason: null,
      source: 'WEB',
      createdTime: new Date(Date.now() - (stockIndex * 3600_000 + i * 600_000)).toISOString(),
      updatedAt: new Date().toISOString(),
      version: 2,
    };
  }));
}
