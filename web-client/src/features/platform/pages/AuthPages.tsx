import { Button, Container, PasswordInput, Stack, TextInput, Title, Text, Anchor } from '@mantine/core';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/app/auth';
import { platformApi } from '../api';

function AuthForm({ register }: { register: boolean }) {
  const { login } = useAuth(); const navigate = useNavigate();
  const [username, setUsername] = useState(''); const [email, setEmail] = useState('');
  const [password, setPassword] = useState(''); const [error, setError] = useState('');
  async function submit() {
    try {
      const result = register ? await platformApi.register({ username, email, password }) : await platformApi.login({ username, password });
      login(result.token, result.user); navigate('/');
    } catch (e) { setError(e instanceof Error ? e.message : 'Request failed'); }
  }
  return <Container size="xs" py="xl"><Title order={2} mb="md">{register ? 'Create account' : 'Sign in'}</Title>
    <Stack><TextInput label="Username" value={username} onChange={e => setUsername(e.currentTarget.value)} />
      {register && <TextInput label="Email" type="email" value={email} onChange={e => setEmail(e.currentTarget.value)} />}
      <PasswordInput label="Password" value={password} onChange={e => setPassword(e.currentTarget.value)} />
      {error && <Text c="red">{error}</Text>}<Button onClick={() => void submit()}>{register ? 'Register' : 'Login'}</Button>
      <Anchor component={Link} to={register ? '/login' : '/register'}>{register ? 'Already registered?' : 'Create an account'}</Anchor>
    </Stack></Container>;
}
export const LoginPage = () => <AuthForm register={false} />;
export const RegisterPage = () => <AuthForm register />;
