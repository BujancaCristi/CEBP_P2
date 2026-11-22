import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect } from 'react';

const wsUrl = () => `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

export function useStomp<T>(destination: string, onMessage: (message: T) => void, enabled = true) {
  useEffect(() => {
    if (!enabled) return;
    const client = new Client({ brokerURL: wsUrl(), reconnectDelay: 3000 });
    client.onConnect = () => client.subscribe(destination, (message: IMessage) => {
      onMessage(JSON.parse(message.body) as T);
    });
    client.activate();
    return () => { void client.deactivate(); };
  }, [destination, enabled, onMessage]);
}
