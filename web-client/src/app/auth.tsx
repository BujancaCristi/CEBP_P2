import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

export type AuthUser = { id: number; username: string; email?: string; role: 'TRADER' | 'ADMIN' | 'MARKET_MAKER' };
type AuthState = { token: string | null; user: AuthUser | null; login: (token: string, user: AuthUser) => void; logout: () => void };
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => localStorage.getItem('exchange.token'));
  const [user, setUser] = useState<AuthUser | null>(() => {
    const value = localStorage.getItem('exchange.user');
    return value ? JSON.parse(value) as AuthUser : null;
  });
  const value = useMemo<AuthState>(() => ({
    token, user,
    login(nextToken, nextUser) {
      localStorage.setItem('exchange.token', nextToken);
      localStorage.setItem('exchange.user', JSON.stringify(nextUser));
      setToken(nextToken); setUser(nextUser);
    },
    logout() {
      localStorage.removeItem('exchange.token'); localStorage.removeItem('exchange.user');
      setToken(null); setUser(null);
    },
  }), [token, user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
