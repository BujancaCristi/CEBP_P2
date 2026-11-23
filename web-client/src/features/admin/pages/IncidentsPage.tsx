import { Badge, Button, Container, Group, Select, Table, Text, Title } from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuth } from '@/app/auth';
import { PageHeader } from '@/app/PageHeader';
import { adminApi, type IncidentStatus } from '../api';

const TYPES = ['ORDER_REJECTED', 'INSUFFICIENT_FUNDS', 'INSUFFICIENT_SHARES', 'CONCURRENCY_CONFLICT', 'PRICE_SPIKE',
  'BOT_INVENTORY_LIMIT', 'EVENT_PUBLISH_FAILED'];
const severityColor = { LOW: 'gray', MEDIUM: 'orange', HIGH: 'red' } as const;

/** Admin > Incidents: list with filters, acknowledge and resolve. Polls every 10 s. */
export function IncidentsPage() {
  const { token } = useAuth();
  const qc = useQueryClient();
  const [status, setStatus] = useState<string | null>('OPEN');
  const [type, setType] = useState<string | null>(null);
  const key = ['s1', 'admin', 'incidents', status, type];
  const incidents = useQuery({
    queryKey: key, queryFn: () => adminApi.incidents(token, status ?? undefined, type ?? undefined),
    enabled: !!token, refetchInterval: 10_000,
  });
  const change = useMutation({
    mutationFn: ({ id, next }: { id: number; next: IncidentStatus }) => adminApi.changeIncident(token, id, next),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['s1', 'admin', 'incidents'] }),
  });

  return (
    <Container size="xl" py="md">
      <PageHeader title="Incidents" subtitle="Things that went wrong, newest first" color="orange" />
      <Group mb="sm">
        <Select label="Status" w={180} clearable value={status} onChange={setStatus} data={['OPEN', 'ACKNOWLEDGED', 'RESOLVED']} />
        <Select label="Type" w={240} clearable value={type} onChange={setType} data={TYPES} />
      </Group>
      {incidents.error && <Text c="red">{(incidents.error as Error).message}</Text>}
      {incidents.data?.length === 0 && <Text c="dimmed">No incidents.</Text>}
      {!!incidents.data?.length && (
        <Table striped>
          <Table.Thead><Table.Tr>
            <Table.Th>Time</Table.Th><Table.Th>Type</Table.Th><Table.Th>Severity</Table.Th><Table.Th>Stock</Table.Th>
            <Table.Th>Message</Table.Th><Table.Th>Status</Table.Th><Table.Th />
          </Table.Tr></Table.Thead>
          <Table.Tbody>{incidents.data.map((i) => (
            <Table.Tr key={i.incidentId}>
              <Table.Td><Text size="sm">{new Date(i.createdAt).toLocaleString()}</Text></Table.Td>
              <Table.Td><Text size="sm">{i.type}</Text></Table.Td>
              <Table.Td><Badge color={severityColor[i.severity]} variant="light">{i.severity}</Badge></Table.Td>
              <Table.Td>{i.symbol ?? '-'}</Table.Td>
              <Table.Td><Text size="sm" lineClamp={2}>{i.message}</Text></Table.Td>
              <Table.Td><Badge variant="outline">{i.status}</Badge></Table.Td>
              <Table.Td>
                <Group gap={4} wrap="nowrap">
                  {i.status === 'OPEN' && <Button size="xs" variant="light" onClick={() => change.mutate({ id: i.incidentId, next: 'ACKNOWLEDGED' })}>Acknowledge</Button>}
                  {i.status !== 'RESOLVED' && <Button size="xs" variant="light" color="teal" onClick={() => change.mutate({ id: i.incidentId, next: 'RESOLVED' })}>Resolve</Button>}
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}</Table.Tbody>
        </Table>
      )}
    </Container>
  );
}
