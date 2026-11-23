import { Group, Paper, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/** Coloured banner at the top of each page. `color` is a Mantine colour name. */
export function PageHeader({ title, subtitle, color = 'indigo', right }: {
  title: string; subtitle?: string; color?: string; right?: ReactNode;
}) {
  return (
    <Paper radius="lg" p="lg" mb="lg" shadow="sm"
      style={{ backgroundImage: `linear-gradient(120deg, var(--mantine-color-${color}-7), var(--mantine-color-${color}-4))` }}>
      <Group justify="space-between" align="center">
        <div>
          <Title order={2} c="white">{title}</Title>
          {subtitle && <Text c="white" opacity={0.85} size="sm">{subtitle}</Text>}
        </div>
        {right}
      </Group>
    </Paper>
  );
}

/** A small coloured stat tile. */
export function StatCard({ label, value, color = 'indigo', valueColor }: {
  label: string; value: ReactNode; color?: string; valueColor?: string;
}) {
  return (
    <Paper radius="md" p="md" shadow="xs" style={{ borderTop: `4px solid var(--mantine-color-${color}-5)` }}>
      <Text c="dimmed" size="xs" tt="uppercase" fw={700}>{label}</Text>
      <Title order={3} c={valueColor}>{value}</Title>
    </Paper>
  );
}
