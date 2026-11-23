import { request } from '@/api/http';
import { MOCK_DATA } from '@/app/mock';

const base = import.meta.env.VITE_S1_URL?.replace(/\/$/, '') ?? '/api/s1';

export type BotConfig = {
  id: number;
  botUserId: number;
  stockId: number;
  symbol: string;
  enabled: boolean;
  spreadBps: number;
  quoteQty: number;
  maxInventory: number;
  skewFactor: number;
  refreshMs: number;
  updatedBy: string;
  updatedAt: string;
  version: number;
};

export type UpdateBotConfigInput = {
  enabled?: boolean;
  spreadBps?: number;
  quoteQty?: number;
  maxInventory?: number;
  skewFactor?: number;
  refreshMs?: number;
};

export type Incident = {
  id: number;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  stockId: number | null;
  symbol: string | null;
  orderId: number | null;
  userId: number | null;
  message: string;
  details: Record<string, unknown> | null;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  createdAt: string;
  resolvedAt: string | null;
};

const initialMockBotConfigs: BotConfig[] = [
  { id: 1, botUserId: 2, stockId: 1, symbol: 'ACME', enabled: true, spreadBps: 80, quoteQty: 50, maxInventory: 2000, skewFactor: 0.5, refreshMs: 2000, updatedBy: 'seed', updatedAt: new Date().toISOString(), version: 0 },
  { id: 2, botUserId: 2, stockId: 2, symbol: 'NOVA', enabled: true, spreadBps: 100, quoteQty: 30, maxInventory: 1500, skewFactor: 0.5, refreshMs: 2000, updatedBy: 'seed', updatedAt: new Date().toISOString(), version: 0 },
  { id: 3, botUserId: 2, stockId: 3, symbol: 'ORBX', enabled: true, spreadBps: 120, quoteQty: 100, maxInventory: 5000, skewFactor: 0.5, refreshMs: 2000, updatedBy: 'seed', updatedAt: new Date().toISOString(), version: 0 },
  { id: 4, botUserId: 2, stockId: 4, symbol: 'VLT', enabled: false, spreadBps: 90, quoteQty: 40, maxInventory: 2000, skewFactor: 0.5, refreshMs: 2000, updatedBy: 'seed', updatedAt: new Date().toISOString(), version: 0 },
];

const initialMockIncidents: Incident[] = [
  { id: 101, type: 'CONCURRENCY_CONFLICT', severity: 'LOW', stockId: 1, symbol: 'ACME', orderId: 42, userId: 3, message: 'Price changed mid-purchase to 102.50', details: {}, status: 'RESOLVED', createdAt: new Date(Date.now() - 3600000).toISOString(), resolvedAt: new Date().toISOString() },
  { id: 102, type: 'BOT_INVENTORY_LIMIT', severity: 'MEDIUM', stockId: 1, symbol: 'ACME', orderId: null, userId: 2, message: 'Bot reached maximum inventory limit of 2000 shares', details: {}, status: 'OPEN', createdAt: new Date(Date.now() - 1800000).toISOString(), resolvedAt: null },
  { id: 103, type: 'INSUFFICIENT_FUNDS', severity: 'LOW', stockId: 2, symbol: 'NOVA', orderId: 55, userId: 4, message: 'Order rejected: insufficient cash balance', details: {}, status: 'ACKNOWLEDGED', createdAt: new Date(Date.now() - 900000).toISOString(), resolvedAt: null },
];

function loadMockBots(): BotConfig[] {
  try {
    const s = localStorage.getItem('mock.botConfigs');
    return s ? JSON.parse(s) : initialMockBotConfigs;
  } catch {
    return initialMockBotConfigs;
  }
}

function loadMockIncidents(): Incident[] {
  try {
    const s = localStorage.getItem('mock.incidents');
    return s ? JSON.parse(s) : initialMockIncidents;
  } catch {
    return initialMockIncidents;
  }
}

let mockBotConfigs: BotConfig[] = loadMockBots();
let mockIncidents: Incident[] = loadMockIncidents();

export const adminApi = {
  listBotConfigs: async (token: string): Promise<BotConfig[]> => {
    if (MOCK_DATA) return mockBotConfigs;
    return request<BotConfig[]>(`${base}/api/admin/bots`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  },

  updateBotConfig: async (token: string, id: number, body: UpdateBotConfigInput): Promise<BotConfig> => {
    if (MOCK_DATA) {
      const idx = mockBotConfigs.findIndex(b => b.id === id);
      if (idx >= 0) {
        mockBotConfigs[idx] = { ...mockBotConfigs[idx], ...body, updatedAt: new Date().toISOString(), version: mockBotConfigs[idx].version + 1 };
        try { localStorage.setItem('mock.botConfigs', JSON.stringify(mockBotConfigs)); } catch {}
        return mockBotConfigs[idx];
      }
    }
    return request<BotConfig>(`${base}/api/admin/bots/${id}`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
  },

  listIncidents: async (token: string, status?: string, type?: string): Promise<Incident[]> => {
    if (MOCK_DATA) {
      let filtered = [...mockIncidents];
      if (status) filtered = filtered.filter(i => i.status === status);
      if (type) filtered = filtered.filter(i => i.type === type);
      return filtered;
    }
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (type) params.append('type', type);
    params.append('size', '100');

    const res = await request<{ content: Incident[] }>(`${base}/api/admin/incidents?${params.toString()}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return res.content;
  },

  updateIncidentStatus: async (token: string, id: number, status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'): Promise<Incident> => {
    if (MOCK_DATA) {
      const item = mockIncidents.find(i => i.id === id);
      if (item) {
        item.status = status;
        if (status === 'RESOLVED') item.resolvedAt = new Date().toISOString();
        try { localStorage.setItem('mock.incidents', JSON.stringify(mockIncidents)); } catch {}
        return item;
      }
    }
    return request<Incident>(`${base}/api/admin/incidents/${id}`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ status }),
    });
  },
};
