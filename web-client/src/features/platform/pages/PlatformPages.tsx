import {
  Badge, Button, Container, Grid, Group, Modal, NumberInput, Paper, Select, Stack, Table, Text, TextInput, Title,
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { useStomp } from '@/app/stomp';
import { MOCK_DATA } from '@/app/mock';
import { tradingApi, tradingKeys } from '@/features/trading/api/tradingApi';
import type { Order, PriceUpdatedEvent } from '@/features/trading/types';
import { MarketMovers } from '@/features/insights';
import { platformApi } from '../api';

const eur = (v: number) => v.toLocaleString(undefined, { style: 'currency', currency: 'EUR' });
const pct = (v: number) => `${v >= 0 ? '+' : ''}${v.toFixed(2)}%`;

function DemoBadge() {
  return MOCK_DATA ? <Badge color="yellow">Demo data</Badge> : null;
}

/** Live last prices: starts from GET /api/stocks and follows /topic/prices. */
function useLivePrices() {
  const { token } = useAuth();
  const stocks = useQuery({ queryKey: ['s1', 'stocks'], queryFn: () => platformApi.stocks(token), enabled: !!token });
  const [live, setLive] = useState<Record<string, number>>({});
  useStomp<PriceUpdatedEvent>('/topic/prices', useCallback((p: PriceUpdatedEvent) => {
    setLive((prev) => ({ ...prev, [p.symbol]: Number(p.value) }));
  }, []), !!token);
  const priceOf = (symbol: string, fallback: number) => live[symbol] ?? fallback;
  return { stocks, priceOf };
}

// ------------------------------------------------------------------------------------------- Market

export function MarketPage() {
  const { token } = useAuth();
  const { stocks, priceOf } = useLivePrices();
  const companies = useQuery({ queryKey: ['s1', 'companies'], queryFn: () => platformApi.companies(token), enabled: !!token });
  const [filter, setFilter] = useState('');
  const names = useMemo(() => Object.fromEntries((companies.data ?? []).map((c) => [c.id, c.name])), [companies.data]);

  const rows = (stocks.data ?? []).filter((s) =>
    `${s.symbol} ${names[s.companyId] ?? ''}`.toLowerCase().includes(filter.toLowerCase()));

  return (
    <Container size="xl" py="md">
      <Group justify="space-between" mb="md"><Title order={2}>Market</Title><DemoBadge /></Group>
      <Grid>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <TextInput placeholder="Filter by symbol or company" value={filter} onChange={(e) => setFilter(e.currentTarget.value)} mb="sm" />
          {stocks.isLoading && <Text c="dimmed">Loading stocks...</Text>}
          {stocks.error && <Text c="red">Could not load stocks: {(stocks.error as Error).message}</Text>}
          <Table striped highlightOnHover>
            <Table.Thead><Table.Tr>
              <Table.Th>Symbol</Table.Th><Table.Th>Company</Table.Th><Table.Th ta="right">Last</Table.Th>
              <Table.Th ta="right">Prev. close</Table.Th><Table.Th ta="right">Change</Table.Th><Table.Th />
            </Table.Tr></Table.Thead>
            <Table.Tbody>{rows.map((s) => {
              const last = priceOf(s.symbol, Number(s.value));
              const change = ((last - Number(s.previousClose)) / Number(s.previousClose)) * 100;
              return (
                <Table.Tr key={s.symbol}>
                  <Table.Td fw={600}><Link to={`/stocks/${s.symbol}`}>{s.symbol}</Link></Table.Td>
                  <Table.Td>{names[s.companyId] ?? ''}</Table.Td>
                  <Table.Td ta="right">{last.toFixed(2)}</Table.Td>
                  <Table.Td ta="right">{Number(s.previousClose).toFixed(2)}</Table.Td>
                  <Table.Td ta="right" c={change >= 0 ? 'teal' : 'red'}>{pct(change)}</Table.Td>
                  <Table.Td><Button component={Link} to={`/stocks/${s.symbol}`} size="xs" variant="light">Trade</Button></Table.Td>
                </Table.Tr>
              );
            })}</Table.Tbody>
          </Table>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 4 }}><MarketMovers /></Grid.Col>
      </Grid>
    </Container>
  );
}

// ---------------------------------------------------------------------------------------- Portfolio

export function PortfolioPage() {
  const { token } = useAuth();
  const me = useQuery({ queryKey: ['s1', 'me'], queryFn: () => platformApi.me(token), enabled: !!token, refetchInterval: 5000 });
  const portfolio = useQuery({ queryKey: ['s1', 'portfolio'], queryFn: () => platformApi.portfolio(token), enabled: !!token, refetchInterval: 5000 });
  const { priceOf } = useLivePrices();
  const positions = portfolio.data ?? [];
  const marketValue = positions.reduce((sum, p) => sum + p.quantity * priceOf(p.symbol, Number(p.lastPrice)), 0);
  const cost = positions.reduce((sum, p) => sum + p.quantity * Number(p.averageCost), 0);
  const account = me.data?.account;

  const card = (label: string, value: string, color?: string) => (
    <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
      <Paper withBorder p="md"><Text c="dimmed" size="sm">{label}</Text><Title order={3} c={color}>{value}</Title></Paper>
    </Grid.Col>
  );

  return (
    <Container size="xl" py="md">
      <Group justify="space-between" mb="md"><Title order={2}>Portfolio</Title><DemoBadge /></Group>
      <Grid mb="md">
        {card('Cash', account ? eur(Number(account.balance)) : '-')}
        {card('Reserved for orders', account ? eur(Number(account.reserved)) : '-')}
        {card('Positions value', eur(marketValue))}
        {card('Unrealised P&L', eur(marketValue - cost), marketValue - cost >= 0 ? 'teal' : 'red')}
      </Grid>
      {positions.length === 0 && !portfolio.isLoading && <Text c="dimmed">No positions yet. Buy something on the Market page.</Text>}
      {positions.length > 0 && (
        <Table striped>
          <Table.Thead><Table.Tr>
            <Table.Th>Symbol</Table.Th><Table.Th ta="right">Quantity</Table.Th><Table.Th ta="right">Reserved</Table.Th>
            <Table.Th ta="right">Avg. cost</Table.Th><Table.Th ta="right">Last</Table.Th><Table.Th ta="right">Value</Table.Th>
            <Table.Th ta="right">P&amp;L</Table.Th>
          </Table.Tr></Table.Thead>
          <Table.Tbody>{positions.map((p) => {
            const last = priceOf(p.symbol, Number(p.lastPrice));
            const pnl = (last - Number(p.averageCost)) * p.quantity;
            return (
              <Table.Tr key={p.symbol}>
                <Table.Td fw={600}><Link to={`/stocks/${p.symbol}`}>{p.symbol}</Link></Table.Td>
                <Table.Td ta="right">{p.quantity.toLocaleString()}</Table.Td>
                <Table.Td ta="right">{p.reserved.toLocaleString()}</Table.Td>
                <Table.Td ta="right">{Number(p.averageCost).toFixed(2)}</Table.Td>
                <Table.Td ta="right">{last.toFixed(2)}</Table.Td>
                <Table.Td ta="right">{eur(last * p.quantity)}</Table.Td>
                <Table.Td ta="right" c={pnl >= 0 ? 'teal' : 'red'}>{eur(pnl)}</Table.Td>
              </Table.Tr>
            );
          })}</Table.Tbody>
        </Table>
      )}
    </Container>
  );
}

// ---------------------------------------------------------------------------------------- My orders

const OPEN = new Set(['PENDING', 'OPEN', 'PARTIALLY_FILLED']);

export function OrdersPage() {
  const { token } = useAuth();
  const qc = useQueryClient();
  const [status, setStatus] = useState<string | null>('ACTIVE');
  const [editing, setEditing] = useState<Order | null>(null);
  const [price, setPrice] = useState<number | string>('');
  const [qty, setQty] = useState<number | string>('');
  const [error, setError] = useState('');
  const orders = useQuery({ queryKey: tradingKeys.myOrders(), queryFn: () => tradingApi.myOrders(token, 0, 200), enabled: !!token, refetchInterval: 4000 });

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: tradingKeys.myOrders() });
    void qc.invalidateQueries({ queryKey: ['s1', 'portfolio'] });
  };
  const cancel = useMutation({ mutationFn: (id: number) => tradingApi.cancelOrder(token, id), onSuccess: refresh, onError: (e) => setError((e as Error).message) });
  const modify = useMutation({
    mutationFn: () => tradingApi.modifyOrder(token, editing!.id, {
      price: price === '' ? undefined : Number(price), qty: qty === '' ? undefined : Number(qty),
    }),
    onSuccess: () => { setEditing(null); refresh(); },
    onError: (e) => setError((e as Error).message),
  });

  const rows = (orders.data?.content ?? []).filter((o) =>
    status === null || status === 'ALL' ? true : status === 'ACTIVE' ? OPEN.has(o.status) : o.status === status);

  return (
    <Container size="xl" py="md">
      <Group justify="space-between" mb="md"><Title order={2}>My orders</Title><DemoBadge /></Group>
      <Select w={220} mb="sm" label="Show" value={status} onChange={setStatus}
        data={[{ value: 'ACTIVE', label: 'Active' }, { value: 'ALL', label: 'All' }, 'FILLED', 'CANCELLED', 'REJECTED']} />
      {error && <Text c="red" mb="sm">{error}</Text>}
      {rows.length === 0 && !orders.isLoading && <Text c="dimmed">No orders.</Text>}
      {rows.length > 0 && (
        <Table striped>
          <Table.Thead><Table.Tr>
            <Table.Th>Time</Table.Th><Table.Th>Symbol</Table.Th><Table.Th>Side</Table.Th><Table.Th>Type</Table.Th>
            <Table.Th ta="right">Price</Table.Th><Table.Th ta="right">Filled</Table.Th><Table.Th>Status</Table.Th><Table.Th />
          </Table.Tr></Table.Thead>
          <Table.Tbody>{rows.map((o) => (
            <Table.Tr key={o.id}>
              <Table.Td>{new Date(o.createdTime).toLocaleString()}</Table.Td>
              <Table.Td fw={600}>{o.symbol}</Table.Td>
              <Table.Td c={o.side === 'BUY' ? 'teal' : 'red'}>{o.side}</Table.Td>
              <Table.Td>{o.type}</Table.Td>
              <Table.Td ta="right">{o.price != null ? Number(o.price).toFixed(2) : 'market'}</Table.Td>
              <Table.Td ta="right">{o.filledQty}/{o.qty}</Table.Td>
              <Table.Td><Badge variant="light" color={o.status === 'FILLED' ? 'teal' : o.status === 'REJECTED' ? 'red' : 'gray'}>{o.status}</Badge>
                {o.rejectReason && <Text size="xs" c="dimmed">{o.rejectReason}</Text>}</Table.Td>
              <Table.Td>{OPEN.has(o.status) && o.status !== 'PENDING' && (
                <Group gap={4}>
                  {o.type === 'LIMIT' && <Button size="xs" variant="light" onClick={() => { setEditing(o); setPrice(o.price ?? ''); setQty(o.qty); setError(''); }}>Modify</Button>}
                  <Button size="xs" variant="light" color="red" loading={cancel.isPending && cancel.variables === o.id} onClick={() => cancel.mutate(o.id)}>Cancel</Button>
                </Group>
              )}</Table.Td>
            </Table.Tr>
          ))}</Table.Tbody>
        </Table>
      )}
      <Modal opened={!!editing} onClose={() => setEditing(null)} title={`Modify order #${editing?.id}`}>
        <Stack>
          <NumberInput label="Price" decimalScale={2} value={price} onChange={setPrice} />
          <NumberInput label="Quantity" min={1} value={qty} onChange={setQty} />
          <Button loading={modify.isPending} onClick={() => modify.mutate()}>Save</Button>
        </Stack>
      </Modal>
    </Container>
  );
}

// ---------------------------------------------------------------------------------------- My trades

export function TradesPage() {
  const { token, user } = useAuth();
  const trades = useQuery({ queryKey: tradingKeys.myTrades(), queryFn: () => tradingApi.myTrades(token, 0, 200), enabled: !!token, refetchInterval: 5000 });
  const [symbol, setSymbol] = useState('');
  const rows = (trades.data?.content ?? []).filter((t) => !symbol || t.symbol.toLowerCase().includes(symbol.toLowerCase()));
  return (
    <Container size="xl" py="md">
      <Group justify="space-between" mb="md"><Title order={2}>My trades</Title><DemoBadge /></Group>
      <TextInput w={220} mb="sm" placeholder="Filter by symbol" value={symbol} onChange={(e) => setSymbol(e.currentTarget.value)} />
      {rows.length === 0 && !trades.isLoading && <Text c="dimmed">No trades yet.</Text>}
      {rows.length > 0 && (
        <Table striped>
          <Table.Thead><Table.Tr>
            <Table.Th>Time</Table.Th><Table.Th>Symbol</Table.Th><Table.Th>Side</Table.Th>
            <Table.Th ta="right">Quantity</Table.Th><Table.Th ta="right">Price</Table.Th><Table.Th ta="right">Total</Table.Th>
          </Table.Tr></Table.Thead>
          <Table.Tbody>{rows.map((t) => {
            const side = t.side ?? (t.buyerUserId === user?.id ? 'BUY' : 'SELL');
            return (
              <Table.Tr key={t.id}>
                <Table.Td>{new Date(t.executedAt).toLocaleString()}</Table.Td>
                <Table.Td fw={600}>{t.symbol}</Table.Td>
                <Table.Td c={side === 'BUY' ? 'teal' : 'red'}>{side}</Table.Td>
                <Table.Td ta="right">{t.qty}</Table.Td>
                <Table.Td ta="right">{Number(t.price).toFixed(2)}</Table.Td>
                <Table.Td ta="right">{eur(Number(t.totalValue))}</Table.Td>
              </Table.Tr>
            );
          })}</Table.Tbody>
        </Table>
      )}
    </Container>
  );
}
