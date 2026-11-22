import { Badge, Container, Grid, Group, Paper, Table, Title, Text } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { platformApi } from '../api';
import { MOCK_DATA } from '@/app/mock';
import { tradingApi } from '@/features/trading/api/tradingApi';
import { MarketMovers } from '@/features/insights/components/MarketMovers';

export function MarketPage() {
  const { token } = useAuth(); const query = useQuery({ queryKey: ['stocks'], queryFn: () => platformApi.stocks(token!), enabled: !!token });
  return <Container py="md">
    <Group justify="space-between"><Title order={2}>Market</Title>{MOCK_DATA && <Badge color="yellow">Demo data</Badge>}</Group>
    <Table mt="md" striped>
      <Table.Thead><Table.Tr><Table.Th>Symbol</Table.Th><Table.Th>Last</Table.Th><Table.Th>Previous close</Table.Th><Table.Th>Change</Table.Th></Table.Tr></Table.Thead>
      <Table.Tbody>{query.data?.map(stock => {
        const change = (stock.value - stock.previousClose) / stock.previousClose * 100;
        return <Table.Tr key={stock.symbol}><Table.Td><Link to={`/stocks/${stock.symbol}`}>{stock.symbol}</Link></Table.Td><Table.Td>{stock.value.toFixed(2)}</Table.Td><Table.Td>{stock.previousClose.toFixed(2)}</Table.Td><Table.Td c={change >= 0 ? 'teal' : 'red'}>{change.toFixed(2)}%</Table.Td></Table.Tr>;
      })}</Table.Tbody>
    </Table>
    {MOCK_DATA && <div style={{ marginTop: 16 }}><MarketMovers /></div>}
  </Container>;
}
export function PortfolioPage() {
  const { token } = useAuth(); const query = useQuery({ queryKey: ['portfolio'], queryFn: () => platformApi.portfolio(token!), enabled: !!token });
  const positions = query.data ?? [];
  const marketValue = positions.reduce((sum, p) => sum + p.quantity * p.lastPrice, 0);
  const cost = positions.reduce((sum, p) => sum + p.quantity * p.averageCost, 0);
  return <Container py="md"><Group justify="space-between"><Title order={2}>Portfolio</Title>{MOCK_DATA && <Badge color="yellow">Demo data</Badge>}</Group>
    {MOCK_DATA && <Grid mt="md"><Grid.Col span={{ base: 12, sm: 4 }}><Paper withBorder p="md"><Text c="dimmed">Cash</Text><Title order={3}>€8,420.50</Title></Paper></Grid.Col><Grid.Col span={{ base: 12, sm: 4 }}><Paper withBorder p="md"><Text c="dimmed">Market value</Text><Title order={3}>{marketValue.toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</Title></Paper></Grid.Col><Grid.Col span={{ base: 12, sm: 4 }}><Paper withBorder p="md"><Text c="dimmed">Unrealized P&amp;L</Text><Title order={3} c={marketValue - cost >= 0 ? 'teal' : 'red'}>{(marketValue - cost).toLocaleString(undefined, { style: 'currency', currency: 'EUR' })}</Title></Paper></Grid.Col></Grid>}
    <Table mt="md" striped><Table.Thead><Table.Tr><Table.Th>Symbol</Table.Th><Table.Th>Quantity</Table.Th><Table.Th>Available</Table.Th><Table.Th>Avg. cost</Table.Th><Table.Th>Last price</Table.Th><Table.Th>P&amp;L</Table.Th></Table.Tr></Table.Thead><Table.Tbody>{positions.map(p => {
      const pnl = (p.lastPrice - p.averageCost) * p.quantity;
      return <Table.Tr key={p.symbol}><Table.Td>{p.symbol}</Table.Td><Table.Td>{p.quantity}</Table.Td><Table.Td>{p.available}</Table.Td><Table.Td>{p.averageCost.toFixed(2)}</Table.Td><Table.Td>{p.lastPrice.toFixed(2)}</Table.Td><Table.Td c={pnl >= 0 ? 'teal' : 'red'}>{pnl.toFixed(2)}</Table.Td></Table.Tr>;
    })}</Table.Tbody></Table>
  </Container>;
}
export function OrdersPage() {
  const { token } = useAuth();
  const query = useQuery({ queryKey: ['mock-my-orders'], queryFn: () => tradingApi.myOrders(token, 0, 50) });
  return <Container py="md"><Group justify="space-between"><Title order={2}>My orders</Title>{MOCK_DATA && <Badge color="yellow">Demo data</Badge>}</Group>
    <Table mt="md" striped><Table.Thead><Table.Tr><Table.Th>Symbol</Table.Th><Table.Th>Side</Table.Th><Table.Th>Type</Table.Th><Table.Th>Price</Table.Th><Table.Th>Filled</Table.Th><Table.Th>Status</Table.Th></Table.Tr></Table.Thead>
    <Table.Tbody>{query.data?.content.map(order => <Table.Tr key={order.id}><Table.Td>{order.symbol}</Table.Td><Table.Td c={order.side === 'BUY' ? 'teal' : 'red'}>{order.side}</Table.Td><Table.Td>{order.type}</Table.Td><Table.Td>{order.price?.toFixed(2) ?? '-'}</Table.Td><Table.Td>{order.filledQty}/{order.qty}</Table.Td><Table.Td>{order.status}</Table.Td></Table.Tr>)}</Table.Tbody></Table>
  </Container>;
}

export function TradesPage() {
  const { token } = useAuth();
  const query = useQuery({ queryKey: ['mock-my-trades'], queryFn: () => tradingApi.myTrades(token, 0, 50) });
  return <Container py="md"><Group justify="space-between"><Title order={2}>My trades</Title>{MOCK_DATA && <Badge color="yellow">Demo data</Badge>}</Group>
    <Table mt="md" striped><Table.Thead><Table.Tr><Table.Th>Trade</Table.Th><Table.Th>Symbol</Table.Th><Table.Th>Quantity</Table.Th><Table.Th>Price</Table.Th><Table.Th>Total</Table.Th><Table.Th>Time</Table.Th></Table.Tr></Table.Thead>
    <Table.Tbody>{query.data?.content.map(trade => <Table.Tr key={trade.id}><Table.Td>#{trade.id}</Table.Td><Table.Td>{trade.symbol}</Table.Td><Table.Td>{trade.qty}</Table.Td><Table.Td>{trade.price.toFixed(2)}</Table.Td><Table.Td>{trade.totalValue.toFixed(2)}</Table.Td><Table.Td>{new Date(trade.executedAt).toLocaleString()}</Table.Td></Table.Tr>)}</Table.Tbody></Table>
  </Container>;
}
