import { S2_BASE_URL } from '../integration';
import type { DailyStat, HistoryPoint, Movers, ReportResult } from '../types';

export class InsightsApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = 'InsightsApiError';
  }
}

async function request<T>(token: string | null, path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${S2_BASE_URL}${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      message = body.detail ?? body.message ?? message;
    } catch {
      /* no body */
    }
    throw new InsightsApiError(res.status, message);
  }
  return (await res.json()) as T;
}

export const insightsApi = {
  daily: (token: string | null, date?: string, symbol?: string) => {
    const q = new URLSearchParams();
    if (date) q.set('date', date);
    if (symbol) q.set('symbol', symbol);
    return request<DailyStat[]>(token, `/api/stats/daily?${q}`);
  },
  history: (token: string | null, symbol: string, days = 30) =>
    request<HistoryPoint[]>(token, `/api/stats/history/${encodeURIComponent(symbol)}?days=${days}`),
  movers: (token: string | null, date?: string, limit = 5) =>
    request<Movers>(token, `/api/stats/movers?limit=${limit}${date ? `&date=${date}` : ''}`),
  runReport: (token: string | null, date?: string) =>
    request<ReportResult>(token, `/api/agent/run${date ? `?date=${date}` : ''}`, { method: 'POST' }),
};

export const insightsKeys = {
  daily: (date?: string, symbol?: string) => ['insights', 'daily', date ?? 'today', symbol ?? 'all'] as const,
  history: (symbol: string, days: number) => ['insights', 'history', symbol, days] as const,
  movers: (date?: string) => ['insights', 'movers', date ?? 'today'] as const,
};
