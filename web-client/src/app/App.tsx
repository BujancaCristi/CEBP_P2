import '@mantine/core/styles.css';
import { AppShell, Badge, Burger, Button, Container, Group, MantineProvider, NavLink, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Link, Navigate, Outlet, RouterProvider, createBrowserRouter, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth, type AuthUser } from './auth';
import { disconnectStomp } from './stomp';
import { MOCK_DATA } from './mock';
import { LoginPage, RegisterPage } from '@/features/platform';
import { platformRoutes } from '@/features/platform/routes';
import { tradingRoutes } from '@/features/trading/routes';
import { insightsRoutes } from '@/features/insights/routes';
import { adminRoutes } from '@/features/admin/routes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      retry: (count, error) => count < 1 && !/\b4\d\d\b/.test(String((error as Error)?.message ?? '')),
    },
  },
});

type MenuItem = { to: string; label: string; roles: AuthUser['role'][] };
const TRADE: AuthUser['role'][] = ['TRADER', 'ADMIN'];
const MENU: MenuItem[] = [
  { to: '/', label: 'Market', roles: TRADE },
  { to: '/portfolio', label: 'Portfolio', roles: TRADE },
  { to: '/orders', label: 'My orders', roles: TRADE },
  { to: '/trades', label: 'My trades', roles: TRADE },
  { to: '/statistics', label: 'Statistics', roles: TRADE },
  { to: '/admin/bots', label: 'Bot settings', roles: ['ADMIN'] },
  { to: '/admin/incidents', label: 'Incidents', roles: ['ADMIN'] },
  { to: '/admin/reports', label: 'Reports', roles: ['ADMIN'] },
];

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    path: '/',
    element: <ProtectedLayout />,
    children: [...platformRoutes, ...tradingRoutes, ...insightsRoutes, ...adminRoutes, { path: '*', element: <NotFound /> }],
  },
]);

function NotFound() {
  return <Container py="xl"><Title order={3}>Not found</Title><Link to="/">Back to the market</Link></Container>;
}

function ProtectedLayout() {
  const { token, user, logout } = useAuth();
  const location = useLocation();
  const [opened, { toggle, close }] = useDisclosure();
  if (!token || !user) {
    return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  if (user.role === 'MARKET_MAKER') {
    return (
      <Container py="xl">
        <Title order={3}>The market maker has no web interface</Title>
        <Text mb="md">It runs headless and is configured by an admin under Bot settings.</Text>
        <Button onClick={() => { disconnectStomp(); logout(); }}>Sign out</Button>
      </Container>
    );
  }
  const adminOnly = location.pathname.startsWith('/admin') && user.role !== 'ADMIN';

  return (
    <AppShell header={{ height: 56 }} navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !opened } }} padding="md">
      <AppShell.Header>
        <Group justify="space-between" px="md" h="100%">
          <Group gap="xs">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={700}>CEBP Exchange</Text>
            {MOCK_DATA && <Badge color="yellow">demo data</Badge>}
          </Group>
          <Group gap="sm">
            <Text size="sm">{user.username} <Badge size="xs" variant="light">{user.role}</Badge></Text>
            <Button size="xs" variant="subtle" onClick={() => { disconnectStomp(); queryClient.clear(); logout(); }}>Sign out</Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs">
        {MENU.filter((m) => m.roles.includes(user.role)).map((m) => (
          <NavLink key={m.to} component={Link} to={m.to} label={m.label} onClick={close}
            active={m.to === '/' ? location.pathname === '/' : location.pathname.startsWith(m.to)} />
        ))}
      </AppShell.Navbar>
      <AppShell.Main>
        {adminOnly ? <Container py="xl"><Title order={3}>Not allowed for your role</Title></Container> : <Outlet />}
      </AppShell.Main>
    </AppShell>
  );
}

export function App() {
  return (
    <MantineProvider defaultColorScheme="auto">
      <QueryClientProvider client={queryClient}>
        <AuthProvider><RouterProvider router={router} /></AuthProvider>
      </QueryClientProvider>
    </MantineProvider>
  );
}
