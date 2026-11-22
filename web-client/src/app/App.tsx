import '@mantine/core/styles.css';
import { AppShell, Group, MantineProvider, NavLink, Text } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Link, Navigate, Outlet, RouterProvider, createBrowserRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { LoginPage, RegisterPage } from '@/features/platform';
import { platformRoutes } from '@/features/platform/routes';
import { tradingRoutes } from '@/features/trading/routes';
import { insightsRoutes } from '@/features/insights/routes';
import { adminRoutes } from '@/features/admin/routes';

const queryClient = new QueryClient();
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/', element: <ProtectedLayout />, children: [...platformRoutes, ...tradingRoutes, ...insightsRoutes, ...adminRoutes] },
]);

function ProtectedLayout() {
  const { token, user, logout } = useAuth();
  if (!token || !user) return <Navigate to="/login" replace />;
  return <AppShell header={{ height: 56 }} navbar={{ width: 220, breakpoint: 'sm' }}>
    <AppShell.Header><Group justify="space-between" px="md" h="100%"><Text fw={700}>CEBP Exchange</Text><Text onClick={logout} style={{ cursor: 'pointer' }}>Sign out</Text></Group></AppShell.Header>
    <AppShell.Navbar p="xs">
      <NavLink component={Link} to="/" label="Market" />
      <NavLink component={Link} to="/portfolio" label="Portfolio" />
      <NavLink component={Link} to="/orders" label="My orders" />
      <NavLink component={Link} to="/trades" label="My trades" />
      <NavLink component={Link} to="/statistics" label="Statistics" />
      {user.role === 'ADMIN' && (
        <>
          <NavLink component={Link} to="/admin/bots" label="Bot settings" />
          <NavLink component={Link} to="/admin/incidents" label="Incidents" />
          <NavLink component={Link} to="/admin/reports" label="Reports" />
        </>
      )}
    </AppShell.Navbar>
    <AppShell.Main><Outlet /></AppShell.Main>
  </AppShell>;
}

export function App() {
  return <MantineProvider><QueryClientProvider client={queryClient}><AuthProvider><RouterProvider router={router} /></AuthProvider></QueryClientProvider></MantineProvider>;
}
