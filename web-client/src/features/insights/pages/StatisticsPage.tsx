import { Container, Group, Skeleton, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { insightsApi, insightsKeys } from '../api/insightsApi';
import { useAuth } from '../integration';

const today = () => new Date().toISOString().slice(0, 10);
const num = (v: number | null, d = 2) => (v == null ? '-' : v.toFixed(d));

/** Daily table per stock with a date picker (native date input, so no extra dependency). */
export function StatisticsPage() {
  const { token } = useAuth();
  const [date, setDate] = useState(today());
  const { data, isLoading, error } = useQuery({
    queryKey: insightsKeys.daily(date),
    queryFn: () => insightsApi.daily(token, date),
    enabled: !!token && !!date,
  });

  return (
    <Container size="lg" py="md">
      <Group justify="space-between" mb="md">
        <Title order={3}>Statistics</Title>
        <TextInput type="date" value={date} onChange={(e) => setDate(e.currentTarget.value)} />
      </Group>
      {isLoading && <Skeleton height={200} />}
      {error && <Text c="red">Could not load statistics</Text>}
      {data && (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Symbol</Table.Th>
              <Table.Th ta="right">Open</Table.Th>
              <Table.Th ta="right">High</Table.Th>
              <Table.Th ta="right">Low</Table.Th>
              <Table.Th ta="right">Close</Table.Th>
              <Table.Th ta="right">Change</Table.Th>
              <Table.Th ta="right">VWAP</Table.Th>
              <Table.Th ta="right">Volume</Table.Th>
              <Table.Th ta="right">Trades</Table.Th>
              <Table.Th ta="right">Placed</Table.Th>
              <Table.Th ta="right">Cancelled</Table.Th>
              <Table.Th ta="right">Rejected</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={12}>
                  <Text c="dimmed">No activity on {date}</Text>
                </Table.Td>
              </Table.Tr>
            )}
            {data.map((s) => (
              <Table.Tr key={s.symbol}>
                <Table.Td>{s.symbol}</Table.Td>
                <Table.Td ta="right">{num(s.open)}</Table.Td>
                <Table.Td ta="right">{num(s.high)}</Table.Td>
                <Table.Td ta="right">{num(s.low)}</Table.Td>
                <Table.Td ta="right">{num(s.close)}</Table.Td>
                <Table.Td ta="right" c={s.changePct >= 0 ? 'teal' : 'red'}>
                  {s.changePct.toFixed(2)}%
                </Table.Td>
                <Table.Td ta="right">{num(s.vwap)}</Table.Td>
                <Table.Td ta="right">{s.volume.toLocaleString()}</Table.Td>
                <Table.Td ta="right">{s.tradeCount}</Table.Td>
                <Table.Td ta="right">{s.ordersPlaced}</Table.Td>
                <Table.Td ta="right">{s.ordersCancelled}</Table.Td>
                <Table.Td ta="right">{s.ordersRejected}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Container>
  );
}
