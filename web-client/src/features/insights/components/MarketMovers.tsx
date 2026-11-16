import { Badge, Group, Paper, Skeleton, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { insightsApi, insightsKeys } from '../api/insightsApi';
import { useAuth } from '../integration';
import type { Mover } from '../types';

function MoverTable({ title, rows, showChange }: { title: string; rows: Mover[]; showChange: boolean }) {
  return (
    <Stack gap={4}>
      <Text fw={600} size="sm">
        {title}
      </Text>
      {rows.length === 0 ? (
        <Text c="dimmed" size="xs">
          nothing yet
        </Text>
      ) : (
        <Table withRowBorders={false} verticalSpacing={2} fz="sm">
          <Table.Tbody>
            {rows.map((m) => (
              <Table.Tr key={m.symbol}>
                <Table.Td>{m.symbol}</Table.Td>
                <Table.Td ta="right">
                  {showChange ? (
                    <Badge color={m.changePct >= 0 ? 'teal' : 'red'} variant="light">
                      {m.changePct >= 0 ? '+' : ''}
                      {m.changePct.toFixed(2)}%
                    </Badge>
                  ) : (
                    `${m.volume.toLocaleString()} sh`
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Stack>
  );
}

/** Gainers, losers and most traded stocks of a day. Used on Cezar's Market page: `<MarketMovers />`. */
export function MarketMovers({ date }: { date?: string }) {
  const { token } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: insightsKeys.movers(date),
    queryFn: () => insightsApi.movers(token, date),
    enabled: !!token,
    refetchInterval: 15_000,
  });

  return (
    <Paper withBorder p="md">
      <Title order={5} mb="sm">
        Market movers
      </Title>
      {isLoading && <Skeleton height={120} />}
      {data && (
        <Group align="flex-start" grow>
          <MoverTable title="Gainers" rows={data.gainers} showChange />
          <MoverTable title="Losers" rows={data.losers} showChange />
          <MoverTable title="Most traded" rows={data.mostTraded} showChange={false} />
        </Group>
      )}
    </Paper>
  );
}
