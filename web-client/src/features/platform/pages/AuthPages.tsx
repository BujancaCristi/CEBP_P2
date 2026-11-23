import { Anchor, Box, Button, Container, Group, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { BRAND } from '@/app/brand';
import { Logo } from '@/app/Logo';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { disconnectStomp } from '@/app/stomp';
import { platformApi } from '../api';

function AuthForm({ register }: { register: boolean }) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (register && password !== confirm) return setError('Passwords do not match');
    if (register && password.length < 8) return setError('Password must have at least 8 characters');
    setBusy(true);
    try {
      const result = register
        ? await platformApi.register({ username, email, password })
        : await platformApi.login({ username, password });
      disconnectStomp(); // reconnect with the new token
      login(result.token, result.user);
      navigate(params.get('returnTo') ?? '/', { replace: true });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Request failed';
      setError(msg.includes('(401)') ? 'Wrong username or password' : msg);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Box mih="100vh" py={60} style={{ backgroundImage: 'linear-gradient(135deg, var(--mantine-color-indigo-9), var(--mantine-color-cyan-6))' }}>
    <Container size={420}>
      <Group justify="center" mb="xs"><Logo size={48} /></Group>
      <Title order={1} ta="center" c="white">{BRAND.name}</Title>
      <Text c="indigo.0" ta="center" mb="lg">{register ? 'Create a trader account with 10 000 EUR of play money' : BRAND.tagline}</Text>
      <Paper p="xl" radius="lg" shadow="xl" component="form" onSubmit={submit}>
        <Title order={3} mb="md">{register ? 'Create account' : 'Sign in'}</Title>
        <Stack>
          <TextInput label="Username" required value={username} onChange={(e) => setUsername(e.currentTarget.value)} />
          {register && <TextInput label="Email" type="email" required value={email} onChange={(e) => setEmail(e.currentTarget.value)} />}
          <PasswordInput label="Password" required value={password} onChange={(e) => setPassword(e.currentTarget.value)} />
          {register && <PasswordInput label="Confirm password" required value={confirm} onChange={(e) => setConfirm(e.currentTarget.value)} />}
          {error && <Text c="red" size="sm">{error}</Text>}
          <Button type="submit" loading={busy} variant="gradient" gradient={BRAND.gradient} size="md">{register ? 'Register' : 'Sign in'}</Button>
          <Anchor component={Link} to={register ? '/login' : '/register'} size="sm">
            {register ? 'Already have an account? Sign in' : 'No account? Register'}
          </Anchor>
        </Stack>
      </Paper>
      {!register && (
        <Text c="indigo.0" size="xs" ta="center" mt="md">
          Demo accounts: trader / trader-password, admin / admin-password
        </Text>
      )}
    </Container>
    </Box>
  );
}

export const LoginPage = () => <AuthForm register={false} />;
export const RegisterPage = () => <AuthForm register />;
