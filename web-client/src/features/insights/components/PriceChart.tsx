import { Paper, SegmentedControl, Skeleton, Text } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { insightsApi, insightsKeys } from '../api/insightsApi';
import { useAuth } from '../integration';

/** Daily close history of one stock from S2. Used on Patric's stock page: `<PriceChart symbol="ACME" />`. */
export function PriceChart({ symbol }: { symbol: string }) {
  const { token } = useAuth();
  const [days, setDays] = useState(30);
  const { data, isLoading, error } = useQuery({
    queryKey: insightsKeys.history(symbol, days),
    queryFn: () => insightsApi.history(token, symbol, days),
    enabled: !!token,
    refetchInterval: 30_000,
  });

  return (
    <Paper withBorder p="md">
      <SegmentedControl
        size="xs"
        value={String(days)}
        onChange={(v) => setDays(Number(v))}
        data={[
          { label: '7d', value: '7' },
          { label: '30d', value: '30' },
          { label: '90d', value: '90' },
        ]}
      />
      {isLoading && <Skeleton height={220} mt="sm" />}
      {error && (
        <Text c="red" size="sm" mt="sm">
          Price history unavailable
        </Text>
      )}
      {data && data.length === 0 && (
        <Text c="dimmed" size="sm" mt="sm">
          No trades yet for {symbol}
        </Text>
      )}
      {data && data.length > 0 && (
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={data} margin={{ top: 12, right: 12, left: 0, bottom: 0 }}>
            <XAxis dataKey="day" tick={{ fontSize: 11 }} />
            <YAxis domain={['auto', 'auto']} tick={{ fontSize: 11 }} width={56} />
            <Tooltip />
            <Line type="monotone" dataKey="close" dot={false} strokeWidth={2} isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </Paper>
  );
}
