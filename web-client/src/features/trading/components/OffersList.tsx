import { useState } from 'react';
import { Button, Card, Group, Loader, Table, Text, Title } from '@mantine/core';
import { useOffers } from '../hooks/useOffers';
import { useAuth } from '../integration';
import { BuyOfferDialog } from './BuyOfferDialog';
import type { BuyOfferResponse, Offer } from '../types';
import { fmt } from '../format';

export interface OffersListProps {
  symbol: string;
  onBought?: (result: BuyOfferResponse) => void;
}

/** Open SELL orders with a Buy button. Buying happens in {@link BuyOfferDialog} (409 flow inside). */
export function OffersList({ symbol, onBought }: OffersListProps) {
  const { user } = useAuth();
  const offers = useOffers(symbol);
  const [selected, setSelected] = useState<Offer | null>(null);

  const canBuy = user?.role === 'TRADER';

  return (
    <Card withBorder>
      <Group justify="space-between" mb="sm">
        <Title order={4}>Offers</Title>
        {offers.isFetching && <Loader size="xs" />}
      </Group>

      {offers.isError && (
        <Text c="red" size="sm">
          Could not load offers: {(offers.error as Error).message}
        </Text>
      )}

      <Table striped highlightOnHover verticalSpacing="xs" fz="sm" data-testid="offers">
        <Table.Thead>
          <Table.Tr>
            <Table.Th>#</Table.Th>
            <Table.Th ta="right">Price</Table.Th>
            <Table.Th ta="right">Shares</Table.Th>
            <Table.Th ta="right">Total</Table.Th>
            <Table.Th>Since</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(offers.data ?? []).length === 0 && !offers.isLoading && (
            <Table.Tr>
              <Table.Td colSpan={6} ta="center" c="dimmed">
                No open offers
              </Table.Td>
            </Table.Tr>
          )}
          {(offers.data ?? []).map((o) => {
            const mine = user?.id === o.sellerUserId;
            return (
              <Table.Tr key={`${o.orderId}-${o.version}`}>
                <Table.Td c="dimmed">{o.orderId}</Table.Td>
                <Table.Td ta="right">{fmt.price(o.price)}</Table.Td>
                <Table.Td ta="right">{fmt.qty(o.remainingQty)}</Table.Td>
                <Table.Td ta="right">{fmt.money(o.totalPrice)}</Table.Td>
                <Table.Td>{fmt.time(o.createdTime)}</Table.Td>
                <Table.Td ta="right">
                  {mine ? (
                    <Text size="xs" c="dimmed">
                      yours
                    </Text>
                  ) : (
                    <Button size="compact-xs" disabled={!canBuy} onClick={() => setSelected(o)}>
                      Buy
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>

      <BuyOfferDialog
        offer={selected}
        onClose={() => setSelected(null)}
        onBought={(r) => {
          onBought?.(r);
        }}
      />
    </Card>
  );
}
