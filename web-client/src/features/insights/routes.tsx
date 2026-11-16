import type { RouteObject } from 'react-router-dom';
import { ReportsPage } from './pages/ReportsPage';
import { StatisticsPage } from './pages/StatisticsPage';

/** Mounted once by Cezar's router (src/app). Paths are relative to the app layout route. */
export const insightsRoutes: RouteObject[] = [
  { path: 'statistics', element: <StatisticsPage /> },
  { path: 'admin/reports', element: <ReportsPage /> },
];
