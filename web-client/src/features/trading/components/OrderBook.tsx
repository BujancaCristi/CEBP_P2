import { Badge, Card, Group, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import type { Book, BookLevel, Trade } from '../types';
import { fmt } from '../format';

export interface OrderBookProps {
  book: Book | undefined;
  lastPrice: number | null;
  trades?: Trade[];
  isLoading?: boolean;
}

/** Live order book: bids (highest first) and asks (lowest first), plus a short trade tape. */
export function OrderBook({ book, lastPrice, trades = [], isLoading }: OrderBookProps) {
  if (isLoading && !book) {
    return (
      <Card withBorder>
        <Group justify="center" p="md">
          <Loader size="sm" />
        </Group>
      </Card>
    );
  }

  return (
    <Card withBorder>
      <Stack gap="sm">
        <Group justify="space-between" align="baseline">
          <Title order={4}>Order book</Title>
          <Group gap="xs">
            <Badge variant="light" color="teal">
              Bid {fmt.price(book?.bestBid)}
            </Badge>
            <Badge variant="light" color="red">
              Ask {fmt.price(book?.bestAsk)}
            </Badge>
            <Badge variant="filled" color="blue">
              Last {fmt.price(lastPrice)}
            </Badge>
          </Group>
        </Group>

        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
          <Side title="Bids" color="teal" levels={book?.bids ?? []} />
          <Side title="Asks" color="red" levels={book?.asks ?? []} />
        </SimpleGrid>

        {trades.length > 0 && (
          <>
            <Text size="sm" fw={500}>
              Last trades
            </Text>
            <Table striped highlightOnHover withTableBorder={false} verticalSpacing={2} fz="xs" data-testid="trade-tape">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Time</Table.Th>
                  <Table.Th ta="right">Price</Table.Th>
                  <Table.Th ta="right">Qty</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {trades.map((t) => (
                  <Table.Tr key={t.id}>
                    <Table.Td>{fmt.time(t.executedAt)}</Table.Td>
                    <Table.Td ta="right">{fmt.price(t.price)}</Table.Td>
                    <Table.Td ta="right">{fmt.qty(t.qty)}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </>
        )}
      </Stack>
    </Card>
  );
}

function Side({ title, color, levels }: { title: string; color: string; levels: BookLevel[] }) {
  const maxQty = Math.max(1, ...levels.map((l) => l.qty));
  return (
    <Stack gap={4}>
      <Text size="sm" fw={500} c={color}>
        {title}
      </Text>
      <Table verticalSpacing={2} fz="sm" withTableBorder={false} data-testid={`book-${title.toLowerCase()}`}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th ta="right">Price</Table.Th>
            <Table.Th ta="right">Qty</Table.Th>
            <Table.Th ta="right">#</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {levels.length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={3} ta="center" c="dimmed">
                empty
              </Table.Td>
            </Table.Tr>
          )}
          {levels.map((l) => (
            <Table.Tr
              key={l.price}
              style={{
                background: `linear-gradient(to left, var(--mantine-color-${color}-light) ${Math.round((l.qty / maxQty) * 100)}%, transparent 0)`,
              }}
            >
              <Table.Td ta="right">{fmt.price(l.price)}</Table.Td>
              <Table.Td ta="right">{fmt.qty(l.qty)}</Table.Td>
              <Table.Td ta="right" c="dimmed">
                {l.orders}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Stack>
  );
}
