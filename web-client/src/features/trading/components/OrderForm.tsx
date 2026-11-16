import { useMemo, useState } from 'react';
import { Alert, Badge, Button, Card, Group, NumberInput, SegmentedControl, Stack, Text, Title } from '@mantine/core';
import { usePlaceOrder } from '../hooks/usePlaceOrder';
import { useMyOrderUpdates } from '../hooks/useMyOrderUpdates';
import { useAuth } from '../integration';
import type { Order, OrderSide, OrderStatus, OrderType } from '../types';
import { fmt } from '../format';

export interface OrderFormProps {
  symbol: string;
  /** Pre-fills the price (e.g. the last trade or best bid/ask). */
  referencePrice?: number | null;
  tickSize?: number;
}

const STATUS_COLOR: Record<OrderStatus, string> = {
  PENDING: 'gray',
  OPEN: 'blue',
  PARTIALLY_FILLED: 'indigo',
  FILLED: 'teal',
  CANCELLED: 'orange',
  REJECTED: 'red',
};

/** Limit / market, buy / sell. Shows the placed order's status as it changes over /user/queue/orders. */
export function OrderForm({ symbol, referencePrice, tickSize = 0.01 }: OrderFormProps) {
  const { user } = useAuth();
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState<number | string>(referencePrice ?? '');
  const [qty, setQty] = useState<number | string>(10);
  const [placed, setPlaced] = useState<Order | null>(null);
  const place = usePlaceOrder();
  const updates = useMyOrderUpdates(symbol);

  const canTrade = user?.role === 'TRADER' || user?.role === 'MARKET_MAKER';
  const numPrice = typeof price === 'number' ? price : Number(price);
  const numQty = typeof qty === 'number' ? qty : Number(qty);
  const decimals = Math.max(0, Math.ceil(-Math.log10(tickSize)));

  const priceError = useMemo(() => {
    if (type === 'MARKET') return undefined;
    if (!(numPrice > 0)) return 'Required for LIMIT orders';
    const ticks = numPrice / tickSize;
    if (Math.abs(ticks - Math.round(ticks)) > 1e-6) return `Must be a multiple of ${tickSize}`;
    return undefined;
  }, [type, numPrice, tickSize]);
  const qtyError = Number.isInteger(numQty) && numQty >= 1 ? undefined : 'Whole number ≥ 1';
  const valid = !priceError && !qtyError;

  const live = placed ? updates[placed.id] : undefined;
  const status: OrderStatus | undefined = live?.status ?? placed?.status;
  const remaining = live?.remainingQty ?? placed?.remainingQty;

  const submit = () => {
    place.mutate(
      {
        symbol,
        side,
        type,
        qty: numQty,
        ...(type === 'LIMIT' ? { price: Number(numPrice.toFixed(decimals)) } : {}),
      },
      { onSuccess: (order) => setPlaced(order) },
    );
  };

  return (
    <Card withBorder>
      <Stack gap="sm">
        <Title order={4}>New order</Title>

        <SegmentedControl
          fullWidth
          value={side}
          onChange={(v) => setSide(v as OrderSide)}
          data={[
            { label: 'Buy', value: 'BUY' },
            { label: 'Sell', value: 'SELL' },
          ]}
          color={side === 'BUY' ? 'teal' : 'red'}
        />
        <SegmentedControl
          fullWidth
          value={type}
          onChange={(v) => setType(v as OrderType)}
          data={[
            { label: 'Limit', value: 'LIMIT' },
            { label: 'Market', value: 'MARKET' },
          ]}
        />

        <NumberInput
          label="Price"
          placeholder={type === 'MARKET' ? 'best available' : undefined}
          disabled={type === 'MARKET'}
          min={tickSize}
          step={tickSize}
          decimalScale={decimals}
          fixedDecimalScale
          value={type === 'MARKET' ? '' : price}
          onChange={setPrice}
          error={type === 'LIMIT' && price !== '' ? priceError : undefined}
        />
        <NumberInput
          label="Quantity"
          min={1}
          step={1}
          allowDecimal={false}
          value={qty}
          onChange={setQty}
          error={qty !== '' ? qtyError : undefined}
        />

        <Text size="sm" c="dimmed">
          {type === 'LIMIT' && valid
            ? `${side === 'BUY' ? 'Reserves' : 'Receives up to'} ${fmt.money(numPrice * numQty)}`
            : type === 'MARKET'
              ? 'Executes against the book now; the unfilled rest is cancelled'
              : ' '}
        </Text>

        {!canTrade && (
          <Alert color="gray" variant="light">
            Log in as a trader to place orders.
          </Alert>
        )}

        {place.isError && (
          <Alert color="red" title={place.error.code}>
            {place.error.message}
            {place.error.problem.errors && (
              <ul style={{ margin: '4px 0 0', paddingLeft: 16 }}>
                {Object.entries(place.error.problem.errors).map(([f, m]) => (
                  <li key={f}>
                    {f}: {m}
                  </li>
                ))}
              </ul>
            )}
          </Alert>
        )}

        <Button
          color={side === 'BUY' ? 'teal' : 'red'}
          disabled={!canTrade || !valid}
          loading={place.isPending}
          onClick={submit}
        >
          {side === 'BUY' ? 'Buy' : 'Sell'} {Number.isFinite(numQty) ? fmt.qty(numQty) : ''} {symbol}
        </Button>

        {placed && status && (
          <Alert
            color={STATUS_COLOR[status]}
            variant="light"
            title={
              <Group gap="xs">
                <span>Order #{placed.id}</span>
                <Badge color={STATUS_COLOR[status]} size="sm">
                  {status}
                </Badge>
              </Group>
            }
            withCloseButton
            onClose={() => setPlaced(null)}
          >
            {status === 'REJECTED'
              ? (live?.reason ?? placed.rejectReason)
              : `${placed.side} ${placed.type} ${fmt.qty(placed.qty)} @ ${placed.price == null ? 'market' : fmt.price(placed.price)}` +
                (remaining != null && remaining !== placed.qty ? ` · ${fmt.qty(placed.qty - remaining)} filled` : '')}
          </Alert>
        )}
      </Stack>
    </Card>
  );
}
