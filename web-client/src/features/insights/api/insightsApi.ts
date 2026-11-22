import { S2_BASE_URL } from '../integration';
import type { DailyStat, HistoryPoint, Movers, ReportResult } from '../types';
import { MOCK_DATA } from '@/app/mock';
import { mockDaily, mockHistory, mockMovers, mockReport } from '../mock';

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
    if (MOCK_DATA) {
      const rows = mockDaily(date ?? new Date().toISOString().slice(0, 10));
      return Promise.resolve(symbol ? rows.filter(r => r.symbol === symbol) : rows);
    }
    const q = new URLSearchParams();
    if (date) q.set('date', date);
    if (symbol) q.set('symbol', symbol);
    return request<DailyStat[]>(token, `/api/stats/daily?${q}`);
  },
  history: (token: string | null, symbol: string, days = 30) =>
    MOCK_DATA ? Promise.resolve(mockHistory(symbol, days))
      : request<HistoryPoint[]>(token, `/api/stats/history/${encodeURIComponent(symbol)}?days=${days}`),
  movers: (token: string | null, date?: string, limit = 5) =>
    MOCK_DATA ? Promise.resolve(mockMovers(date ?? new Date().toISOString().slice(0, 10)))
      : request<Movers>(token, `/api/stats/movers?limit=${limit}${date ? `&date=${date}` : ''}`),
  runReport: (token: string | null, date?: string) =>
    MOCK_DATA ? Promise.resolve({ ...mockReport, date: date ?? mockReport.date })
      : request<ReportResult>(token, `/api/agent/run${date ? `?date=${date}` : ''}`, { method: 'POST' }),
};

export const insightsKeys = {
  daily: (date?: string, symbol?: string) => ['insights', 'daily', date ?? 'today', symbol ?? 'all'] as const,
  history: (symbol: string, days: number) => ['insights', 'history', symbol, days] as const,
  movers: (date?: string) => ['insights', 'movers', date ?? 'today'] as const,
};
