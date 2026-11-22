import { request } from '@/api/http';
import { MOCK_DATA, mockStocks } from '@/app/mock';

const base = import.meta.env.VITE_S1_URL?.replace(/\/$/, '') ?? '/api/s1';
const auth = (token: string | null): RequestInit => ({ headers: token ? { Authorization: `Bearer ${token}` } : {} });

export type Role = 'TRADER' | 'ADMIN' | 'MARKET_MAKER';
export type AuthResponse = { token: string; user: { id: number; username: string; email: string; role: Role }; accountId: number };
export type Stock = { id: number; companyId: number; symbol: string; value: number; previousClose: number; tickSize: number };
export type Company = { id: number; name: string; sector: string };
export type Position = { symbol: string; quantity: number; reserved: number; available: number; averageCost: number; lastPrice: number };
export type Me = {
  user: { id: number; username: string; email: string; role: Role };
  account: { id: number; currency: string; balance: number; reserved: number; available: number };
};

export const platformApi = {
  login: (body: { username: string; password: string }) =>
    request<AuthResponse>(`${base}/api/auth/login`, { method: 'POST', body: JSON.stringify(body) }),
  register: (body: { username: string; email: string; password: string }) =>
    request<AuthResponse>(`${base}/api/auth/register`, { method: 'POST', body: JSON.stringify(body) }),

  me: (token: string | null): Promise<Me> => MOCK_DATA
    ? Promise.resolve({
        user: { id: 1, username: 'demo', email: 'demo@example.com', role: 'TRADER' },
        account: { id: 1, currency: 'EUR', balance: 8420.5, reserved: 250, available: 8170.5 },
      })
    : request<Me>(`${base}/api/me`, auth(token)),

  stocks: (token: string | null): Promise<Stock[]> => MOCK_DATA
    ? Promise.resolve(mockStocks.map((s, i) => ({ id: i + 1, companyId: i + 1, tickSize: 0.01, ...s })))
    : request<Stock[]>(`${base}/api/stocks`, auth(token)),

  companies: (token: string | null): Promise<Company[]> => MOCK_DATA
    ? Promise.resolve(mockStocks.map((s, i) => ({ id: i + 1, name: s.name, sector: '' })))
    : request<Company[]>(`${base}/api/companies`, auth(token)),

  portfolio: (token: string | null): Promise<Position[]> => MOCK_DATA
    ? Promise.resolve([
        { symbol: 'ACME', quantity: 120, reserved: 20, available: 100, averageCost: 118.24, lastPrice: 124.82 },
        { symbol: 'NOVA', quantity: 75, reserved: 0, available: 75, averageCost: 72.18, lastPrice: 68.35 },
      ])
    : request<Position[]>(`${base}/api/portfolio`, auth(token)),
};
