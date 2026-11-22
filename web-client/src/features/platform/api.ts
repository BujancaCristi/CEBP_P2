import { request } from '@/api/http';
import { MOCK_DATA, mockStocks } from '@/app/mock';
const base = import.meta.env.VITE_S1_URL?.replace(/\/$/, '') ?? '/api/s1';
export type AuthResponse = { token: string; user: { id: number; username: string; email: string; role: 'TRADER' | 'ADMIN' | 'MARKET_MAKER' }; accountId: number };
export const platformApi = {
  login: (body: { username: string; password: string }) => {
    if (MOCK_DATA) {
      const isAdmin = !body.username || body.username.toLowerCase().includes('admin');
      return Promise.resolve<AuthResponse>({
        token: 'mock-jwt-token',
        user: {
          id: isAdmin ? 1 : 2,
          username: body.username || 'admin',
          email: `${body.username || 'admin'}@example.com`,
          role: isAdmin ? 'ADMIN' : 'TRADER',
        },
        accountId: isAdmin ? 1 : 2,
      });
    }
    return request<AuthResponse>(`${base}/api/auth/login`, { method: 'POST', body: JSON.stringify(body) });
  },
  register: (body: { username: string; email: string; password: string }) => {
    if (MOCK_DATA) {
      return Promise.resolve<AuthResponse>({
        token: 'mock-jwt-token',
        user: {
          id: 3,
          username: body.username,
          email: body.email,
          role: 'TRADER',
        },
        accountId: 3,
      });
    }
    return request<AuthResponse>(`${base}/api/auth/register`, { method: 'POST', body: JSON.stringify(body) });
  },
  stocks: (token: string) => MOCK_DATA
    ? Promise.resolve(mockStocks)
    : request<Array<{ symbol: string; value: number; previousClose: number }>>(`${base}/api/stocks`, { headers: { Authorization: `Bearer ${token}` } }),
  portfolio: (token: string) => MOCK_DATA
    ? Promise.resolve([
        { symbol: 'ACME', quantity: 120, available: 100, averageCost: 118.24, lastPrice: 124.82 },
        { symbol: 'NOVA', quantity: 75, available: 75, averageCost: 72.18, lastPrice: 68.35 },
        { symbol: 'ORBX', quantity: 200, available: 180, averageCost: 40.55, lastPrice: 43.70 },
      ])
    : request<Array<{ symbol: string; quantity: number; available: number; averageCost: number; lastPrice: number }>>(`${base}/api/portfolio`, { headers: { Authorization: `Bearer ${token}` } }),
};
