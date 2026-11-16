/** TypeScript mirror of contracts/s2-openapi.yaml. */
export interface DailyStat {
  symbol: string;
  day: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  vwap: number | null;
  volume: number;
  tradeCount: number;
  ordersPlaced: number;
  ordersCancelled: number;
  ordersRejected: number;
  changePct: number;
}

export interface HistoryPoint {
  day: string;
  open: number | null;
  high: number | null;
  low: number | null;
  close: number | null;
  volume: number;
}

export interface Mover {
  symbol: string;
  open: number | null;
  close: number | null;
  changePct: number;
  volume: number;
  tradeCount: number;
}

export interface Movers {
  date: string;
  gainers: Mover[];
  losers: Mover[];
  mostTraded: Mover[];
}

export interface ReportResult {
  date: string;
  sent: boolean;
  report: string | null;
  toolCalls: number;
  generatedAt: string;
  error: string | null;
}
