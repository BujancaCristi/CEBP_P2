import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useEffect, useRef } from 'react';
import { MOCK_DATA } from './mock';

/**
 * One shared STOMP connection to /ws for the whole app. The JWT goes on the CONNECT frame, so
 * /user/queue/* destinations reach the logged-in user. Subscriptions survive reconnects.
 */
type Listener = { destination: string; handler: (body: unknown) => void; sub?: StompSubscription };

const listeners = new Set<Listener>();
let client: Client | null = null;
let clientToken: string | null = null;

const wsUrl = () => `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

function subscribe(l: Listener) {
  if (client?.connected) {
    l.sub = client.subscribe(l.destination, (m: IMessage) => l.handler(JSON.parse(m.body)));
  }
}

function ensureClient() {
  const token = localStorage.getItem('exchange.token');
  if (client && clientToken === token) return;
  if (client) void client.deactivate();
  clientToken = token;
  client = new Client({
    brokerURL: wsUrl(),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
  });
  client.onConnect = () => listeners.forEach(subscribe);
  client.activate();
}

/** Closes the connection (on logout). */
export function disconnectStomp() {
  listeners.forEach((l) => (l.sub = undefined));
  if (client) void client.deactivate();
  client = null;
  clientToken = null;
}

export function useStomp<T>(destination: string, onMessage: (message: T) => void, enabled = true) {
  const handlerRef = useRef(onMessage);
  handlerRef.current = onMessage;

  useEffect(() => {
    if (!enabled || MOCK_DATA) return;
    ensureClient();
    const l: Listener = { destination, handler: (body) => handlerRef.current(body as T) };
    listeners.add(l);
    subscribe(l);
    return () => {
      listeners.delete(l);
      l.sub?.unsubscribe();
    };
  }, [destination, enabled]);
}
