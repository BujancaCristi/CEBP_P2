import { useState, useEffect } from 'react';
import {
  Title, Text, Card, Group, Stack, Table, Badge, Button,
  Select, Alert, Loader, Box
} from '@mantine/core';
import { useAuth } from '@/app/auth';
import { adminApi, type Incident } from '../api/adminApi';

const INCIDENT_TYPES = [
  'ORDER_REJECTED',
  'INSUFFICIENT_FUNDS',
  'INSUFFICIENT_SHARES',
  'CONCURRENCY_CONFLICT',
  'PRICE_SPIKE',
  'BOT_INVENTORY_LIMIT',
  'EVENT_PUBLISH_FAILED',
];

export function IncidentsPage() {
  const { token } = useAuth();
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const loadData = async () => {
    if (!token) return;
    try {
      setLoading(true);
      setError(null);
      const data = await adminApi.listIncidents(
        token,
        statusFilter || undefined,
        typeFilter || undefined
      );
      setIncidents(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load incidents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [token, statusFilter, typeFilter]);

  const handleStatusChange = async (incidentId: number, newStatus: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED') => {
    if (!token) return;
    try {
      setUpdatingId(incidentId);
      const updated = await adminApi.updateIncidentStatus(token, incidentId, newStatus);
      setIncidents(prev => prev.map(i => i.id === updated.id ? updated : i));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update incident status');
    } finally {
      setUpdatingId(null);
    }
  };

  const severityColor = (sev: string) => {
    switch (sev) {
      case 'HIGH': return 'red';
      case 'MEDIUM': return 'orange';
      case 'LOW': return 'blue';
      default: return 'gray';
    }
  };

  const statusColor = (st: string) => {
    switch (st) {
      case 'RESOLVED': return 'teal';
      case 'ACKNOWLEDGED': return 'yellow';
      case 'OPEN': return 'red';
      default: return 'gray';
    }
  };

  return (
    <Stack gap="lg" p="md">
      <Group justify="space-between" align="center">
        <div>
          <Title order={2}>Admin &rsaquo; Incidents</Title>
          <Text c="dimmed" size="sm">
            Monitor trading anomalies, concurrency conflicts, and operational issues.
          </Text>
        </div>
        <Button variant="light" onClick={loadData} loading={loading}>
          Refresh
        </Button>
      </Group>

      {error && (
        <Alert color="red" title="Error" withCloseButton onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Filter Bar */}
      <Card withBorder p="sm" radius="md">
        <Group>
          <Select
            label="Status"
            placeholder="All Statuses"
            clearable
            data={[
              { value: 'OPEN', label: 'Open' },
              { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
              { value: 'RESOLVED', label: 'Resolved' },
            ]}
            value={statusFilter}
            onChange={setStatusFilter}
          />
          <Select
            label="Incident Type"
            placeholder="All Types"
            clearable
            data={INCIDENT_TYPES.map(t => ({ value: t, label: t }))}
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ minWidth: 260 }}
          />
        </Group>
      </Card>

      {/* Table */}
      {loading && incidents.length === 0 ? (
        <Group justify="center" p="xl"><Loader /></Group>
      ) : incidents.length === 0 ? (
        <Card withBorder p="xl" ta="center">
          <Text c="dimmed">No incidents found matching the filter criteria.</Text>
        </Card>
      ) : (
        <Card withBorder radius="md">
          <Table striped highlightOnHover verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>ID</Table.Th>
                <Table.Th>Time</Table.Th>
                <Table.Th>Type</Table.Th>
                <Table.Th>Severity</Table.Th>
                <Table.Th>Stock</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th style={{ width: '40%' }}>Message</Table.Th>
                <Table.Th>Actions</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {incidents.map((inc) => (
                <Table.Tr key={inc.id}>
                  <Table.Td>#{inc.id}</Table.Td>
                  <Table.Td>
                    <Text size="xs">
                      {new Date(inc.createdAt).toLocaleString()}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge variant="outline" size="sm">{inc.type}</Badge>
                  </Table.Td>
                  <Table.Td>
                    <Badge color={severityColor(inc.severity)} variant="filled" size="sm">
                      {inc.severity}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    {inc.symbol ? <Text fw={600}>{inc.symbol}</Text> : <Text c="dimmed">-</Text>}
                  </Table.Td>
                  <Table.Td>
                    <Badge color={statusColor(inc.status)} variant="light" size="sm">
                      {inc.status}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{inc.message}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Group gap="xs">
                      {inc.status === 'OPEN' && (
                        <Button
                          size="xs"
                          variant="light"
                          color="yellow"
                          loading={updatingId === inc.id}
                          onClick={() => handleStatusChange(inc.id, 'ACKNOWLEDGED')}
                        >
                          Ack
                        </Button>
                      )}
                      {inc.status !== 'RESOLVED' && (
                        <Button
                          size="xs"
                          variant="light"
                          color="teal"
                          loading={updatingId === inc.id}
                          onClick={() => handleStatusChange(inc.id, 'RESOLVED')}
                        >
                          Resolve
                        </Button>
                      )}
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      )}
    </Stack>
  );
}
