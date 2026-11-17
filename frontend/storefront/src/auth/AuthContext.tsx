import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, customerApi } from '../api/endpoints';
import { onSessionExpired } from '../api/client';
import { clearTokens, readTokens, writeTokens } from './tokenStore';
import type { AuthenticationResult, Customer, RegistrationResult } from '../api/types';

interface AuthState {
  customer: Customer | null;
  loading: boolean;
  signedIn: boolean;
  register: (body: { fullName: string; email: string; mobile: string; password: string }) => Promise<RegistrationResult>;
  login: (identifier: string, password: string) => Promise<AuthenticationResult>;
  logout: () => Promise<void>;
  refreshCustomer: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [loading, setLoading] = useState(true);

  // On boot, if a token is stored, resolve the customer so a reload keeps the
  // session. A failure here just means the stored token is gone or stale.
  useEffect(() => {
    let cancelled = false;
    async function boot() {
      if (!readTokens()) {
        setLoading(false);
        return;
      }
      try {
        const me = await customerApi.me();
        if (!cancelled) setCustomer(me);
      } catch {
        clearTokens();
        if (!cancelled) setCustomer(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void boot();
    return () => {
      cancelled = true;
    };
  }, []);

  // A failed refresh anywhere in the app drops the session everywhere.
  useEffect(
    () =>
      onSessionExpired(() => {
        setCustomer(null);
      }),
    [],
  );

  const register = useCallback(
    (body: { fullName: string; email: string; mobile: string; password: string }) =>
      authApi.register(body),
    [],
  );

  const login = useCallback(async (identifier: string, password: string) => {
    const result = await authApi.login(identifier, password);
    writeTokens(result.tokens);
    setCustomer(result.customer);
    return result;
  }, []);

  const logout = useCallback(async () => {
    const tokens = readTokens();
    // Best-effort revoke; the local session is dropped regardless, so a network
    // failure cannot strand the customer in a signed-in shell.
    if (tokens) {
      try {
        await authApi.logout(tokens.refreshToken);
      } catch {
        /* ignored on purpose */
      }
    }
    clearTokens();
    setCustomer(null);
  }, []);

  const refreshCustomer = useCallback(async () => {
    const me = await customerApi.me();
    setCustomer(me);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      customer,
      loading,
      signedIn: customer !== null,
      register,
      login,
      logout,
      refreshCustomer,
    }),
    [customer, loading, register, login, logout, refreshCustomer],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
