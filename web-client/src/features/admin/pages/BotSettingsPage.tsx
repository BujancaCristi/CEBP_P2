import { Alert, Badge, Button, Container, Group, NumberInput, Paper, Stack, Switch, Table, Text, Title } from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { PageHeader } from '@/app/PageHeader';
import { adminApi, type BotConfig, type BotConfigUpdate } from '../api';

const keys = { bots: ['s1', 'admin', 'bots'] as const };

/** Admin > Bot settings. /admin/bots lists them; /admin/bots/:id edits one, pre-filled from the report's Apply link. */
export function BotSettingsPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const bots = useQuery({ queryKey: keys.bots, queryFn: () => adminApi.bots(token), enabled: !!token, refetchInterval: 10_000 });
  const selected = id ? bots.data?.find((b) => b.configId === Number(id)) : undefined;

  return (
    <Container size="xl" py="md">
      <PageHeader title="Bot settings" subtitle="How the market maker quotes each stock" color="grape" />
      {bots.error && <Alert color="red" mb="md">{(bots.error as Error).message}</Alert>}
      {id && selected && <EditForm key={selected.configId + ':' + selected.version} config={selected} />}
      {id && bots.data && !selected && <Alert color="red" mb="md">No bot setting with id {id}.</Alert>}
      <Table striped highlightOnHover>
        <Table.Thead><Table.Tr>
          <Table.Th>Stock</Table.Th><Table.Th>Enabled</Table.Th><Table.Th ta="right">Spread (bps)</Table.Th>
          <Table.Th ta="right">Quote qty</Table.Th><Table.Th ta="right">Max inventory</Table.Th><Table.Th ta="right">Skew</Table.Th>
          <Table.Th ta="right">Refresh (ms)</Table.Th><Table.Th>Last change</Table.Th><Table.Th />
        </Table.Tr></Table.Thead>
        <Table.Tbody>{(bots.data ?? []).map((b) => (
          <Table.Tr key={b.configId} bg={b.configId === Number(id) ? 'var(--mantine-color-blue-light)' : undefined}>
            <Table.Td fw={600}>{b.symbol}</Table.Td>
            <Table.Td><Badge color={b.enabled ? 'teal' : 'gray'} variant="light">{b.enabled ? 'on' : 'off'}</Badge></Table.Td>
            <Table.Td ta="right">{b.spreadBps}</Table.Td>
            <Table.Td ta="right">{b.quoteQty}</Table.Td>
            <Table.Td ta="right">{b.maxInventory.toLocaleString()}</Table.Td>
            <Table.Td ta="right">{Number(b.skewFactor).toFixed(2)}</Table.Td>
            <Table.Td ta="right">{b.refreshMs}</Table.Td>
            <Table.Td><Text size="xs">{b.updatedBy}, {new Date(b.updatedAt).toLocaleString()}</Text></Table.Td>
            <Table.Td><Button component={Link} to={`/admin/bots/${b.configId}`} size="xs" variant="light">Edit</Button></Table.Td>
          </Table.Tr>
        ))}</Table.Tbody>
      </Table>
    </Container>
  );
}

function EditForm({ config }: { config: BotConfig }) {
  const { token } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const num = (name: keyof BotConfig) => (params.has(name) ? Number(params.get(name)) : (config[name] as number));
  const recommended = ['spreadBps', 'quoteQty', 'maxInventory', 'enabled'].some((k) => params.has(k));

  const [form, setForm] = useState({
    enabled: params.has('enabled') ? params.get('enabled') === 'true' : config.enabled,
    spreadBps: num('spreadBps'), quoteQty: num('quoteQty'), maxInventory: num('maxInventory'),
    skewFactor: Number(config.skewFactor), refreshMs: config.refreshMs,
  });
  const [saved, setSaved] = useState(false);
  useEffect(() => setSaved(false), [form]);

  const save = useMutation({
    mutationFn: (body: BotConfigUpdate) => adminApi.updateBot(token, config.configId, body),
    onSuccess: () => {
      setSaved(true);
      void qc.invalidateQueries({ queryKey: keys.bots });
      navigate(`/admin/bots/${config.configId}`, { replace: true }); // drop the recommendation query
    },
  });

  const set = <K extends keyof typeof form>(k: K) => (v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <Paper withBorder p="md" mb="lg">
      <Group justify="space-between" mb="sm">
        <Title order={4}>Edit {config.symbol}</Title>
        <Button variant="subtle" component={Link} to="/admin/bots">Close</Button>
      </Group>
      {recommended && (
        <Alert color="blue" mb="sm" title="Recommended by the nightly report">
          {params.get('reason') ?? 'Values below were pre-filled from the report.'} Nothing changes until you press Save.
        </Alert>
      )}
      <Stack maw={420}>
        <Switch label="Quoting enabled" checked={form.enabled} onChange={(e) => set('enabled')(e.currentTarget.checked)} />
        <NumberInput label={`Spread (bps), now ${config.spreadBps}`} min={1} max={2000} value={form.spreadBps} onChange={(v) => set('spreadBps')(Number(v))} />
        <NumberInput label={`Quote quantity, now ${config.quoteQty}`} min={1} value={form.quoteQty} onChange={(v) => set('quoteQty')(Number(v))} />
        <NumberInput label={`Max inventory, now ${config.maxInventory}`} min={1} value={form.maxInventory} onChange={(v) => set('maxInventory')(Number(v))} />
        <NumberInput label="Skew factor (0 to 1)" min={0} max={1} step={0.05} decimalScale={3} value={form.skewFactor} onChange={(v) => set('skewFactor')(Number(v))} />
        <NumberInput label="Refresh (ms)" min={250} step={250} value={form.refreshMs} onChange={(v) => set('refreshMs')(Number(v))} />
        {save.error && <Text c="red">{(save.error as Error).message}</Text>}
        {saved && <Text c="teal">Saved. The market maker picks it up within a couple of seconds.</Text>}
        <Group><Button loading={save.isPending} onClick={() => save.mutate(form)}>Save</Button></Group>
      </Stack>
    </Paper>
  );
}
