import { Alert, Button, Container, Group, Paper, Text, TextInput, Title } from '@mantine/core';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { insightsApi } from '../api/insightsApi';
import { useAuth } from '../integration';
import { PageHeader } from '@/app/PageHeader';

const yesterday = () => new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);

/** Admin > Reports: "Run report now" with a date picker; shows the returned report. Reports are not stored. */
export function ReportsPage() {
  const { token } = useAuth();
  const [date, setDate] = useState(yesterday());
  const run = useMutation({ mutationFn: () => insightsApi.runReport(token, date) });

  return (
    <Container size="md" py="md">
      <PageHeader title="Nightly report" subtitle="Run the AI market report now; it is emailed and shown here" color="grape" />
      <Group mb="md">
        <TextInput type="date" value={date} onChange={(e) => setDate(e.currentTarget.value)} />
        <Button onClick={() => run.mutate()} loading={run.isPending}>
          Run report now
        </Button>
      </Group>
      {run.isPending && <Text c="dimmed">The agent is reading the day and writing the report (up to a minute)...</Text>}
      {run.error && <Alert color="red">{(run.error as Error).message}</Alert>}
      {run.data && (
        <>
          <Alert color={run.data.sent ? 'teal' : 'red'} mb="md">
            {run.data.sent
              ? `Report for ${run.data.date} emailed (${run.data.toolCalls} tool calls).`
              : `Report failed: ${run.data.error ?? 'send_report was not called'}. A "report failed" email was sent.`}
          </Alert>
          {run.data.report && (
            <Paper withBorder p="md">
              <ReactMarkdown>{run.data.report}</ReactMarkdown>
            </Paper>
          )}
        </>
      )}
    </Container>
  );
}
