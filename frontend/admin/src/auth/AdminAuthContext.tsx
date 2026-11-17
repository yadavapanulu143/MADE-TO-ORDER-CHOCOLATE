import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { adminAuthApi } from '../api/endpoints';
import { onSessionExpired } from '../api/client';
import { clearTokens, readTokens, writeTokens } from './tokenStore';
import type { AdminView } from '../api/types';

interface AdminAuthState {
  admin: AdminView | null;
  loading: boolean;
  signedIn: boolean;
  /** True when the signed-in administrator holds the named permission. */
  can: (permission: string) => boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthState | null>(null);

// Decoding the access token for its claims is safe here: the token was just minted
// by the identity service and is only ever sent back to it. The permissions shown
// are a convenience for hiding controls the server would refuse anyway — the
// backend is the authority, and every action is authorised again downstream.
function claimsOf(accessToken: string): AdminView | null {
  try {
    const payload = accessToken.split('.')[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as {
      sub?: string;
      role?: string;
      permissions?: string[];
      fullName?: string;
      email?: string;
      name?: string;
    };
    return {
      id: json.sub ?? '',
      fullName: json.fullName ?? json.name ?? json.email ?? 'Administrator',
      email: json.email ?? '',
      role: json.role ?? 'ADMIN',
      permissions: json.permissions ?? [],
      active: true,
      createdAt: '',
    };
  } catch {
    return null;
  }
}

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminView | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const tokens = readTokens();
    if (!tokens) {
      setLoading(false);
      return;
    }
    setAdmin(claimsOf(tokens.accessToken));
    setLoading(false);
  }, []);

  useEffect(
    () =>
      onSessionExpired(() => {
        setAdmin(null);
      }),
    [],
  );

  const login = useCallback(async (email: string, password: string) => {
    const result = await adminAuthApi.login(email, password);
    writeTokens(result.tokens);
    setAdmin(result.admin);
  }, []);

  const logout = useCallback(async () => {
    const tokens = readTokens();
    // Best-effort revoke; the local session is dropped regardless, so a network
    // failure cannot strand the administrator in a signed-in shell.
    if (tokens) {
      try {
        await adminAuthApi.logout(tokens.refreshToken);
      } catch {
        /* ignored on purpose */
      }
    }
    clearTokens();
    setAdmin(null);
  }, []);

  const can = useCallback(
    (permission: string) => {
      if (!admin) return false;
      if (admin.role === 'SUPER_ADMIN') return true;
      return admin.permissions.includes(permission);
    },
    [admin],
  );

  const value = useMemo<AdminAuthState>(
    () => ({ admin, loading, signedIn: admin !== null, can, login, logout }),
    [admin, loading, can, login, logout],
  );

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}

export function useAdminAuth(): AdminAuthState {
  const context = useContext(AdminAuthContext);
  if (!context) throw new Error('useAdminAuth must be used within an AdminAuthProvider');
  return context;
}
