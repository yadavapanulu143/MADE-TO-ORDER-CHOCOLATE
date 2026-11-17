import type { TokenPair } from '../api/types';

// Administrator tokens live in localStorage under their own key, deliberately
// separate from any customer session, so a browser signed into the storefront is
// not signed into the dashboard. The dashboard is a privileged surface: an access
// token is short-lived and the refresh token rotates on every use.

const STORAGE_KEY = 'choco.admin.tokens';

export function readTokens(): TokenPair | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as TokenPair;
    if (!parsed.accessToken || !parsed.refreshToken) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function writeTokens(tokens: TokenPair): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
}

export function clearTokens(): void {
  window.localStorage.removeItem(STORAGE_KEY);
}

export function accessToken(): string | null {
  return readTokens()?.accessToken ?? null;
}
