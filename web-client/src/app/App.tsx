import '@mantine/core/styles.css';
import { AppShell, Badge, Burger, Button, Container, Group, MantineProvider, NavLink, Text, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Link, Navigate, Outlet, RouterProvider, createBrowserRouter, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth, type AuthUser } from './auth';
import { disconnectStomp } from './stomp';
import { MOCK_DATA } from './mock';
import { BRAND, theme } from './brand';
import { Logo } from './Logo';
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
  const navItem = (m: MenuItem, color: string) => (
    <NavLink key={m.to} component={Link} to={m.to} label={m.label} onClick={close} variant="filled" color={color}
      style={{ borderRadius: 8, marginBottom: 2 }} fw={500}
      active={m.to === '/' ? location.pathname === '/' : location.pathname.startsWith(m.to)} />
  );

  const items = MENU.filter((m) => m.roles.includes(user.role));
  const roleColor = user.role === 'ADMIN' ? 'grape' : 'teal';

  return (
    <AppShell header={{ height: 60 }} navbar={{ width: 230, breakpoint: 'sm', collapsed: { mobile: !opened } }} padding="md">
      <AppShell.Header style={{ backgroundImage: 'linear-gradient(120deg, var(--mantine-color-indigo-8), var(--mantine-color-cyan-6))', border: 0 }}>
        <Group justify="space-between" px="md" h="100%">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" color="white" />
            <Logo />
            <Text fw={800} c="white" size="lg" visibleFrom="xs">{BRAND.name}</Text>
            {MOCK_DATA && <Badge color="yellow" variant="filled">demo data</Badge>}
          </Group>
          <Group gap="sm">
            <Text size="sm" c="white" fw={500} visibleFrom="xs">{user.username}</Text>
            <Badge color={roleColor} variant="filled" size="sm">{user.role}</Badge>
            <Button size="xs" variant="white" color="indigo" onClick={() => { disconnectStomp(); queryClient.clear(); logout(); }}>Sign out</Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="sm" style={{ background: 'var(--mantine-color-indigo-0)' }}>
        <Text size="xs" fw={700} c="indigo.7" tt="uppercase" mb={4} px="xs">Trading</Text>
        {items.filter((m) => !m.to.startsWith('/admin')).map((m) => navItem(m, 'indigo'))}
        {items.some((m) => m.to.startsWith('/admin')) && (
          <Text size="xs" fw={700} c="grape.7" tt="uppercase" mt="md" mb={4} px="xs">Admin</Text>
        )}
        {items.filter((m) => m.to.startsWith('/admin')).map((m) => navItem(m, 'grape'))}
      </AppShell.Navbar>
      <AppShell.Main bg="gray.0">
        {adminOnly ? <Container py="xl"><Title order={3}>Not allowed for your role</Title></Container> : <Outlet />}
      </AppShell.Main>
    </AppShell>
  );
}

export function App() {
  return (
    <MantineProvider theme={theme} defaultColorScheme="light">
      <QueryClientProvider client={queryClient}>
        <AuthProvider><RouterProvider router={router} /></AuthProvider>
      </QueryClientProvider>
    </MantineProvider>
  );
}
