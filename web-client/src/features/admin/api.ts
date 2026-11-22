import { request } from '@/api/http';
import { MOCK_DATA } from '@/app/mock';

const base = import.meta.env.VITE_S1_URL?.replace(/\/$/, '') ?? '/api/s1';
const auth = (token: string | null): Record<string, string> => (token ? { Authorization: `Bearer ${token}` } : {});

export type BotConfig = {
  configId: number; botUserId: number; symbol: string; enabled: boolean; spreadBps: number; quoteQty: number;
  maxInventory: number; skewFactor: number; refreshMs: number; updatedBy: string; updatedAt: string; version: number;
};
export type BotConfigUpdate = Partial<Pick<BotConfig, 'enabled' | 'spreadBps' | 'quoteQty' | 'maxInventory' | 'skewFactor' | 'refreshMs'>>;
export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type Incident = {
  incidentId: number; type: string; severity: 'LOW' | 'MEDIUM' | 'HIGH'; symbol: string | null; orderId: number | null;
  userId: number | null; message: string; status: IncidentStatus; createdAt: string;
};

const mockBots: BotConfig[] = ['ACME', 'NOVA', 'ORBX', 'VLT'].map((symbol, i) => ({
  configId: i + 1, botUserId: 2, symbol, enabled: true, spreadBps: 50, quoteQty: 20, maxInventory: 10000,
  skewFactor: 0.5, refreshMs: 2000, updatedBy: 'seed', updatedAt: new Date().toISOString(), version: 0,
}));
const mockIncidents: Incident[] = [
  { incidentId: 1, type: 'CONCURRENCY_CONFLICT', severity: 'LOW', symbol: 'ACME', orderId: 12, userId: 3,
    message: 'buy-from-offer PRICE_CHANGED', status: 'OPEN', createdAt: new Date().toISOString() },
  { incidentId: 2, type: 'BOT_INVENTORY_LIMIT', severity: 'MEDIUM', symbol: 'NOVA', orderId: null, userId: 2,
    message: 'Market maker reached max inventory on NOVA', status: 'ACKNOWLEDGED', createdAt: new Date().toISOString() },
];

export const adminApi = {
  bots: (token: string | null) => MOCK_DATA ? Promise.resolve(mockBots)
    : request<BotConfig[]>(`${base}/api/admin/bots`, { headers: auth(token) }),
  updateBot: (token: string | null, id: number, body: BotConfigUpdate) => MOCK_DATA
    ? Promise.resolve({ ...mockBots.find((b) => b.configId === id)!, ...body })
    : request<BotConfig>(`${base}/api/admin/bots/${id}`, { method: 'PUT', headers: auth(token), body: JSON.stringify(body) }),
  incidents: (token: string | null, status?: string, type?: string) => {
    if (MOCK_DATA) return Promise.resolve(mockIncidents.filter((i) => (!status || i.status === status) && (!type || i.type === type)));
    const q = new URLSearchParams();
    if (status) q.set('status', status);
    if (type) q.set('type', type);
    return request<Incident[]>(`${base}/api/admin/incidents?${q}`, { headers: auth(token) });
  },
  changeIncident: (token: string | null, id: number, status: IncidentStatus) => MOCK_DATA
    ? Promise.resolve({ ...mockIncidents.find((i) => i.incidentId === id)!, status })
    : request<Incident>(`${base}/api/admin/incidents/${id}`, { method: 'PATCH', headers: auth(token), body: JSON.stringify({ status }) }),
};
