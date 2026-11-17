import type { TokenPair } from '../api/types';

// Tokens live in localStorage so a reload keeps the session. This is a bearer-token
// SPA, so there is no cookie for the browser to protect; the trade-off is that any
// script running on this origin could read the token, which is why the backend
// keeps access tokens short-lived and rotates refresh tokens on every use.

const STORAGE_KEY = 'choco.tokens';

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

/** True when the access token is within 15s of expiry, so a refresh is worth trying. */
export function accessTokenExpiringSoon(): boolean {
  const tokens = readTokens();
  if (!tokens?.accessTokenExpiresAt) return false;
  const expires = new Date(tokens.accessTokenExpiresAt).getTime();
  if (Number.isNaN(expires)) return false;
  return expires - Date.now() < 15_000;
}
