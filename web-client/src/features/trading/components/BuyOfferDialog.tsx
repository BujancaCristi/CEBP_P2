import { useEffect, useState } from 'react';
import { Alert, Button, Group, Modal, NumberInput, Stack, Text } from '@mantine/core';
import { useBuyOffer } from '../hooks/useBuyOffer';
import type { BuyOfferResponse, Offer } from '../types';
import { fmt } from '../format';

export interface BuyOfferDialogProps {
  /** The offer as the user saw it when clicking Buy; null closes the dialog. */
  offer: Offer | null;
  onClose: () => void;
  onBought?: (result: BuyOfferResponse) => void;
}

type Step =
  | { kind: 'input' }
  /** 409 PRICE_CHANGED: ask the user to confirm the new price/quantity (case 2). */
  | { kind: 'confirm'; current: Offer }
  /** 409 NO_LONGER_AVAILABLE: gone or too few shares (case 1). */
  | { kind: 'gone'; current: Offer | null }
  | { kind: 'done'; result: BuyOfferResponse }
  | { kind: 'error'; message: string };

/**
 * "Buy from offer" flow. Sends {qty, maxPrice, seenVersion} exactly as the user saw the offer; if
 * S1 answers 409 PRICE_CHANGED the dialog shows the new price and resends with the new version
 * only after the user confirms. Nothing is ever bought at a price the user did not see.
 */
export function BuyOfferDialog({ offer, onClose, onBought }: BuyOfferDialogProps) {
  const [qty, setQty] = useState<number | string>(offer?.remainingQty ?? 1);
  const [step, setStep] = useState<Step>({ kind: 'input' });
  const buy = useBuyOffer();

  useEffect(() => {
    setQty(offer?.remainingQty ?? 1);
    setStep({ kind: 'input' });
    buy.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [offer?.orderId]);

  if (!offer) return null;

  const requestedQty = typeof qty === 'number' ? qty : Number(qty);
  const qtyValid = Number.isInteger(requestedQty) && requestedQty >= 1 && requestedQty <= offer.remainingQty;

  const send = (seen: Offer, buyQty: number) => {
    buy.mutate(
      { orderId: seen.orderId, symbol: seen.symbol, qty: buyQty, maxPrice: seen.price, seenVersion: seen.version },
      {
        onSuccess: (result) => {
          setStep({ kind: 'done', result });
          onBought?.(result);
        },
        onError: (err) => {
          if (err.status === 409 && err.code === 'PRICE_CHANGED' && err.currentOffer) {
            setStep({ kind: 'confirm', current: err.currentOffer });
          } else if (err.status === 409) {
            setStep({ kind: 'gone', current: err.currentOffer ?? null });
          } else {
            setStep({ kind: 'error', message: err.message });
          }
        },
      },
    );
  };

  return (
    <Modal opened onClose={onClose} title={`Buy ${offer.symbol} from offer #${offer.orderId}`} centered>
      <Stack gap="md">
        {step.kind === 'input' && (
          <>
            <Text size="sm">
              {fmt.qty(offer.remainingQty)} shares at <b>{fmt.price(offer.price)}</b> each
              {' · '}seller #{offer.sellerUserId}
            </Text>
            <NumberInput
              label="Quantity"
              min={1}
              max={offer.remainingQty}
              step={1}
              allowDecimal={false}
              value={qty}
              onChange={setQty}
              error={!qtyValid ? `1 to ${offer.remainingQty}` : undefined}
              data-autofocus
            />
            <Text size="sm" c="dimmed">
              Total {fmt.money(qtyValid ? requestedQty * offer.price : null)}
            </Text>
            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                Cancel
              </Button>
              <Button onClick={() => send(offer, requestedQty)} disabled={!qtyValid} loading={buy.isPending}>
                Buy at {fmt.price(offer.price)}
              </Button>
            </Group>
          </>
        )}

        {step.kind === 'confirm' && (
          <>
            <Alert color="yellow" title="Price changed">
              This offer changed while you were looking at it. It is now{' '}
              <b>{fmt.price(step.current.price)}</b> (was {fmt.price(offer.price)}) for{' '}
              {fmt.qty(step.current.remainingQty)} shares.
            </Alert>
            <Text size="sm">
              Buy {fmt.qty(Math.min(requestedQty, step.current.remainingQty))} at {fmt.price(step.current.price)}{' '}
              = {fmt.money(Math.min(requestedQty, step.current.remainingQty) * step.current.price)}?
            </Text>
            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                No, cancel
              </Button>
              <Button
                color="yellow"
                loading={buy.isPending}
                onClick={() => send(step.current, Math.min(requestedQty, step.current.remainingQty))}
              >
                Yes, buy at {fmt.price(step.current.price)}
              </Button>
            </Group>
          </>
        )}

        {step.kind === 'gone' && (
          <>
            <Alert color="red" title="No longer available">
              {step.current && step.current.remainingQty > 0
                ? `Only ${fmt.qty(step.current.remainingQty)} shares are left in this offer.`
                : 'Somebody else bought this offer first.'}
            </Alert>
            <Group justify="flex-end">
              {step.current && step.current.remainingQty > 0 && (
                <Button
                  variant="light"
                  onClick={() => {
                    setQty(step.current!.remainingQty);
                    send(step.current!, step.current!.remainingQty);
                  }}
                  loading={buy.isPending}
                >
                  Buy the remaining {fmt.qty(step.current.remainingQty)}
                </Button>
              )}
              <Button onClick={onClose}>Close</Button>
            </Group>
          </>
        )}

        {step.kind === 'done' && (
          <>
            <Alert color="teal" title="Bought">
              {fmt.qty(step.result.trade.qty)} × {fmt.price(step.result.trade.price)} ={' '}
              {fmt.money(step.result.trade.totalValue)} (trade #{step.result.trade.id})
            </Alert>
            <Group justify="flex-end">
              <Button onClick={onClose}>Close</Button>
            </Group>
          </>
        )}

        {step.kind === 'error' && (
          <>
            <Alert color="red" title="Could not buy">
              {step.message}
            </Alert>
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setStep({ kind: 'input' })}>
                Back
              </Button>
              <Button onClick={onClose}>Close</Button>
            </Group>
          </>
        )}
      </Stack>
    </Modal>
  );
}
