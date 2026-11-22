import type { RouteObject } from 'react-router-dom';
import { MarketPage, PortfolioPage, OrdersPage, TradesPage } from './pages/PlatformPages';
export const platformRoutes: RouteObject[] = [
  { index: true, element: <MarketPage /> },
  { path: 'portfolio', element: <PortfolioPage /> },
  { path: 'orders', element: <OrdersPage /> },
  { path: 'trades', element: <TradesPage /> },
];
