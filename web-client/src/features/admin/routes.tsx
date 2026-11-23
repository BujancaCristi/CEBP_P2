import type { RouteObject } from 'react-router-dom';
import { BotSettingsPage } from './pages/BotSettingsPage';
import { IncidentsPage } from './pages/IncidentsPage';

/**
 * Admin routes. <b>Owner: Paul.</b>
 * Mounted in Cezar's router in src/app/App.tsx.
 */
export const adminRoutes: RouteObject[] = [
  { path: 'admin/bots', element: <BotSettingsPage /> },
  { path: 'admin/incidents', element: <IncidentsPage /> },
];
