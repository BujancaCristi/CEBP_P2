import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Title, Text, Card, Group, Stack, Table, Badge, Switch, NumberInput,
  Button, Alert, Modal, Grid, ActionIcon, Loader, Box
} from '@mantine/core';
import { useAuth } from '@/app/auth';
import { adminApi, type BotConfig, type UpdateBotConfigInput } from '../api/adminApi';

export function BotSettingsPage() {
  const { token } = useAuth();
  const [searchParams] = useSearchParams();

  const [configs, setConfigs] = useState<BotConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Modal edit state
  const [selectedConfig, setSelectedConfig] = useState<BotConfig | null>(null);
  const [editForm, setEditForm] = useState<UpdateBotConfigInput>({});
  const [saving, setSaving] = useState(false);

  // URL recommendation params
  const paramConfigId = searchParams.get('configId');
  const paramSpreadBps = searchParams.get('spreadBps');
  const paramQuoteQty = searchParams.get('quoteQty');
  const paramMaxInventory = searchParams.get('maxInventory');
  const paramSkewFactor = searchParams.get('skewFactor');
  const paramRefreshMs = searchParams.get('refreshMs');
  const paramEnabled = searchParams.get('enabled');
  const paramReason = searchParams.get('reason');

  const hasRecommendation = Boolean(paramConfigId && (paramSpreadBps || paramQuoteQty || paramMaxInventory || paramEnabled !== null));

  const loadData = async () => {
    if (!token) return;
    try {
      setLoading(true);
      setError(null);
      const data = await adminApi.listBotConfigs(token);
      setConfigs(data);

      // Pre-fill from URL recommendation if provided
      if (paramConfigId) {
        const targetId = parseInt(paramConfigId, 10);
        const match = data.find(c => c.id === targetId);
        if (match) {
          openEditModal(match, {
            spreadBps: paramSpreadBps ? parseInt(paramSpreadBps, 10) : match.spreadBps,
            quoteQty: paramQuoteQty ? parseInt(paramQuoteQty, 10) : match.quoteQty,
            maxInventory: paramMaxInventory ? parseInt(paramMaxInventory, 10) : match.maxInventory,
            skewFactor: paramSkewFactor ? parseFloat(paramSkewFactor) : match.skewFactor,
            refreshMs: paramRefreshMs ? parseInt(paramRefreshMs, 10) : match.refreshMs,
            enabled: paramEnabled !== null ? paramEnabled === 'true' : match.enabled,
          });
        }
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load bot configs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [token, paramConfigId]);

  const openEditModal = (config: BotConfig, prefill?: UpdateBotConfigInput) => {
    setSelectedConfig(config);
    setEditForm(prefill || {
      spreadBps: config.spreadBps,
      quoteQty: config.quoteQty,
      maxInventory: config.maxInventory,
      skewFactor: config.skewFactor,
      refreshMs: config.refreshMs,
      enabled: config.enabled,
    });
  };

  const handleSave = async () => {
    if (!token || !selectedConfig) return;
    try {
      setSaving(true);
      setError(null);
      const updated = await adminApi.updateBotConfig(token, selectedConfig.id, editForm);
      setConfigs(prev => prev.map(c => c.id === updated.id ? updated : c));
      setSuccessMsg(`Successfully updated bot settings for ${selectedConfig.symbol}!`);
      setSelectedConfig(null);
      setTimeout(() => setSuccessMsg(null), 5000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update bot config');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleEnabled = async (config: BotConfig) => {
    if (!token) return;
    try {
      const nextState = !config.enabled;
      const updated = await adminApi.updateBotConfig(token, config.id, { enabled: nextState });
      setConfigs(prev => prev.map(c => c.id === updated.id ? updated : c));
      setSuccessMsg(`Market maker ${nextState ? 'enabled' : 'disabled'} for ${config.symbol}`);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to toggle bot status');
    }
  };

  return (
    <Stack gap="lg" p="md">
      <Group justify="space-between" align="center">
        <div>
          <Title order={2}>Admin &rsaquo; Bot Settings</Title>
          <Text c="dimmed" size="sm">
            Control automated market maker parameters, spreads, and inventory risk limits.
          </Text>
        </div>
        <Button variant="light" onClick={loadData} loading={loading}>
          Refresh
        </Button>
      </Group>

      {hasRecommendation && paramReason && (
        <Alert color="blue" title="Recommended by Nightly Report" withCloseButton>
          <Text size="sm">
            <strong>Reason:</strong> {paramReason}
          </Text>
          <Text size="xs" mt={4} c="dimmed">
            The recommended parameters have been pre-filled. Review and click 'Save Changes' to apply.
          </Text>
        </Alert>
      )}

      {successMsg && (
        <Alert color="green" title="Saved" withCloseButton onClose={() => setSuccessMsg(null)}>
          {successMsg}
        </Alert>
      )}

      {error && (
        <Alert color="red" title="Error" withCloseButton onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading && configs.length === 0 ? (
        <Group justify="center" p="xl"><Loader /></Group>
      ) : (
        <Card withBorder radius="md">
          <Table striped highlightOnHover verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Symbol</Table.Th>
                <Table.Th>Status</Table.Th>
                <Table.Th>Spread (bps)</Table.Th>
                <Table.Th>Quote Qty</Table.Th>
                <Table.Th>Max Inventory</Table.Th>
                <Table.Th>Skew Factor</Table.Th>
                <Table.Th>Refresh (ms)</Table.Th>
                <Table.Th>Last Updated</Table.Th>
                <Table.Th>Actions</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {configs.map((config) => (
                <Table.Tr key={config.id}>
                  <Table.Td>
                    <Text fw={700}>{config.symbol}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Switch
                      checked={config.enabled}
                      onChange={() => handleToggleEnabled(config)}
                      label={config.enabled ? 'Active' : 'Disabled'}
                      color="teal"
                      size="sm"
                    />
                  </Table.Td>
                  <Table.Td>
                    <Badge color="blue" variant="light">
                      {config.spreadBps} bps ({(config.spreadBps / 100).toFixed(2)}%)
                    </Badge>
                  </Table.Td>
                  <Table.Td>{config.quoteQty.toLocaleString()}</Table.Td>
                  <Table.Td>{config.maxInventory.toLocaleString()}</Table.Td>
                  <Table.Td>{config.skewFactor}</Table.Td>
                  <Table.Td>{config.refreshMs} ms</Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {new Date(config.updatedAt).toLocaleTimeString()} by {config.updatedBy}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Button
                      size="xs"
                      variant="subtle"
                      onClick={() => openEditModal(config)}
                    >
                      Edit
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Card>
      )}

      {/* Edit Modal */}
      <Modal
        opened={Boolean(selectedConfig)}
        onClose={() => setSelectedConfig(null)}
        title={
          <Group>
            <Title order={4}>Edit Settings for {selectedConfig?.symbol}</Title>
            {hasRecommendation && String(selectedConfig?.id) === paramConfigId && (
              <Badge color="indigo">Report Recommendation</Badge>
            )}
          </Group>
        }
        size="lg"
      >
        <Stack gap="md">
          {hasRecommendation && String(selectedConfig?.id) === paramConfigId && paramReason && (
            <Alert color="blue">
              <strong>Recommendation context:</strong> {paramReason}
            </Alert>
          )}

          <Switch
            label="Market Maker Enabled"
            description="When enabled, bot continually maintains bids and asks on this stock"
            checked={Boolean(editForm.enabled)}
            onChange={(e) => setEditForm({ ...editForm, enabled: e.currentTarget.checked })}
          />

          <Grid>
            <Grid.Col span={6}>
              <NumberInput
                label="Spread (Basis Points)"
                description="100 bps = 1.00% difference between bid and ask"
                min={1}
                max={1000}
                value={editForm.spreadBps}
                onChange={(val) => setEditForm({ ...editForm, spreadBps: typeof val === 'number' ? val : undefined })}
              />
            </Grid.Col>
            <Grid.Col span={6}>
              <NumberInput
                label="Quote Quantity"
                description="Number of shares quoted on each side"
                min={1}
                value={editForm.quoteQty}
                onChange={(val) => setEditForm({ ...editForm, quoteQty: typeof val === 'number' ? val : undefined })}
              />
            </Grid.Col>
            <Grid.Col span={6}>
              <NumberInput
                label="Max Inventory"
                description="Ceiling limit; bot stops buying once reached"
                min={1}
                value={editForm.maxInventory}
                onChange={(val) => setEditForm({ ...editForm, maxInventory: typeof val === 'number' ? val : undefined })}
              />
            </Grid.Col>
            <Grid.Col span={6}>
              <NumberInput
                label="Skew Factor"
                description="Sensitivity of price shift when holding inventory"
                min={0}
                max={1}
                step={0.05}
                decimalScale={3}
                value={editForm.skewFactor}
                onChange={(val) => setEditForm({ ...editForm, skewFactor: typeof val === 'number' ? val : undefined })}
              />
            </Grid.Col>
            <Grid.Col span={6}>
              <NumberInput
                label="Refresh Interval (ms)"
                description="Quote cancellation and replacement cycle"
                min={200}
                step={200}
                value={editForm.refreshMs}
                onChange={(val) => setEditForm({ ...editForm, refreshMs: typeof val === 'number' ? val : undefined })}
              />
            </Grid.Col>
          </Grid>

          <Group justify="flex-end" mt="md">
            <Button variant="default" onClick={() => setSelectedConfig(null)}>
              Cancel
            </Button>
            <Button color="blue" onClick={handleSave} loading={saving}>
              Save Changes
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
