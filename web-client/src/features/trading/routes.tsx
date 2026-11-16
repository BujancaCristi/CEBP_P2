import type { RouteObject } from 'react-router-dom';
import { StockPage } from './pages/StockPage';

/** Mounted once by Cezar's router (src/app). Paths are relative to the app layout route. */
export const tradingRoutes: RouteObject[] = [
  { path: 'stocks/:symbol', element: <StockPage /> },
];
