import type { RouteObject } from 'react-router-dom';
import { BotSettingsPage } from './pages/BotSettingsPage';
import { IncidentsPage } from './pages/IncidentsPage';

/** Mounted once by the app router. Paths are relative to the app layout route. */
export const adminRoutes: RouteObject[] = [
  { path: 'admin/bots', element: <BotSettingsPage /> },
  { path: 'admin/bots/:id', element: <BotSettingsPage /> },
  { path: 'admin/incidents', element: <IncidentsPage /> },
];
