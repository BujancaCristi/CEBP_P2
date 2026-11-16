import { useParams } from 'react-router-dom';
import { Alert, Grid, Group, Stack, Text, Title } from '@mantine/core';
import { PriceChart } from '../integration';
import { useOrderBook } from '../hooks/useOrderBook';
import { OrderBook } from '../components/OrderBook';
import { OffersList } from '../components/OffersList';
import { OrderForm } from '../components/OrderForm';
import { fmt } from '../format';

/**
 * /stocks/:symbol — price chart (insights), live order book, offers with Buy, order form.
 */
export function StockPage() {
  const { symbol: raw } = useParams<{ symbol: string }>();
  const symbol = (raw ?? '').toUpperCase();
  const { book, trades, lastPrice, isLoading, error } = useOrderBook(symbol);

  if (!symbol) {
    return <Alert color="red">No stock symbol in the URL.</Alert>;
  }

  return (
    <Stack gap="md">
      <Group justify="space-between" align="baseline">
        <Title order={2}>{symbol}</Title>
        <Text size="xl" fw={600}>
          {fmt.price(lastPrice)}
        </Text>
      </Group>

      {error && (
        <Alert color="red" title="Stock unavailable">
          {(error as Error).message}
        </Alert>
      )}

      <PriceChart symbol={symbol} />

      <Grid gutter="md">
        <Grid.Col span={{ base: 12, md: 7 }}>
          <Stack gap="md">
            <OrderBook book={book} lastPrice={lastPrice} trades={trades} isLoading={isLoading} />
            <OffersList symbol={symbol} />
          </Stack>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <OrderForm symbol={symbol} referencePrice={lastPrice} />
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
