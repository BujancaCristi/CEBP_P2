export const MOCK_DATA =
  (import.meta.env.VITE_MOCK_DATA as string | undefined)?.toLowerCase() !== 'false';

export const mockStocks = [
  { symbol: 'ACME', name: 'ACME Technologies', value: 124.82, previousClose: 121.40 },
  { symbol: 'NOVA', name: 'Nova Energy', value: 68.35, previousClose: 70.10 },
  { symbol: 'ORBX', name: 'Orbex Logistics', value: 43.70, previousClose: 42.90 },
  { symbol: 'VLT', name: 'Violet Telecom', value: 91.12, previousClose: 93.05 },
];
