import type { DailyStat, HistoryPoint, Movers, ReportResult } from './types';

export function mockDaily(date: string): DailyStat[] {
  const rows = [
    ['ACME', 121.4, 126.2, 119.8, 124.82, 18450, 486],
    ['NOVA', 70.1, 70.85, 67.5, 68.35, 13220, 312],
    ['ORBX', 42.9, 44.25, 42.6, 43.7, 9600, 228],
    ['VLT', 93.05, 94.1, 90.7, 91.12, 7450, 174],
  ] as const;
  return rows.map(([symbol, open, high, low, close, volume, tradeCount]) => ({
    symbol, day: date, open, high, low, close,
    vwap: (open + high + low + close) / 4,
    volume, tradeCount,
    ordersPlaced: tradeCount * 3,
    ordersCancelled: tradeCount,
    ordersRejected: Math.floor(tradeCount / 20),
    changePct: (close - open) / open * 100,
  }));
}

export function mockHistory(symbol: string, days: number): HistoryPoint[] {
  const seed: Record<string, number> = { ACME: 124.82, NOVA: 68.35, ORBX: 43.7, VLT: 91.12 };
  let close = seed[symbol] ?? 100;
  return Array.from({ length: days }, (_, i) => {
    const offset = days - 1 - i;
    close = close - ((i % 5) - 2) * 0.45;
    const open = close - 0.8;
    return {
      day: new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10),
      open,
      high: close + 1.2,
      low: close - 1.4,
      close,
      volume: 4000 + (i * 137) % 6000,
    };
  });
}

export function mockMovers(date: string): Movers {
  const daily = mockDaily(date);
  const gainers = daily.filter(d => d.changePct >= 0).map(d => ({
    symbol: d.symbol, open: d.open, close: d.close, changePct: d.changePct,
    volume: d.volume, tradeCount: d.tradeCount,
  }));
  const losers = daily.filter(d => d.changePct < 0).map(d => ({
    symbol: d.symbol, open: d.open, close: d.close, changePct: d.changePct,
    volume: d.volume, tradeCount: d.tradeCount,
  }));
  return {
    date,
    gainers,
    losers,
    mostTraded: gainers.concat(losers).sort((a, b) => b.volume - a.volume).slice(0, 3),
  };
}

export const mockReport: ReportResult = {
  date: new Date().toISOString().slice(0, 10),
  sent: true,
  toolCalls: 5,
  generatedAt: new Date().toISOString(),
  error: null,
  report: `# Demo nightly market report

## Overview
ACME led today's activity while NOVA declined.

## Per-stock findings
- **ACME:** +2.82%; highest close and volume.
- **NOVA:** -2.49%; margin call and inventory exposure should be monitored.

## Incidents
- 8 low-severity insufficient-funds events.
- 3 inventory-limit incidents for NOVA.

## Recommendations
Consider reducing the NOVA quote quantity and widening its spread.`,
};
