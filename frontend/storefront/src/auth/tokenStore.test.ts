import { beforeEach, describe, expect, it, vi } from 'vitest';
import { accessToken, accessTokenExpiringSoon, clearTokens, readTokens, writeTokens } from './tokenStore';
import type { TokenPair } from '../api/types';

function pair(expiresInMs: number): TokenPair {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    accessTokenExpiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    tokenType: 'Bearer',
  };
}

describe('tokenStore', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('round-trips a token pair', () => {
    writeTokens(pair(60_000));
    expect(readTokens()?.accessToken).toBe('access-1');
    expect(accessToken()).toBe('access-1');
  });

  it('returns null when nothing is stored', () => {
    expect(readTokens()).toBeNull();
    expect(accessToken()).toBeNull();
  });

  it('treats a corrupt entry as signed out rather than throwing', () => {
    window.localStorage.setItem('choco.tokens', '{not json');
    expect(readTokens()).toBeNull();
  });

  it('rejects a partial pair that could not authenticate anything', () => {
    window.localStorage.setItem('choco.tokens', JSON.stringify({ accessToken: 'a' }));
    expect(readTokens()).toBeNull();
  });

  it('clears the session', () => {
    writeTokens(pair(60_000));
    clearTokens();
    expect(readTokens()).toBeNull();
  });

  it('reports an expiring token as expiring soon', () => {
    writeTokens(pair(5_000));
    expect(accessTokenExpiringSoon()).toBe(true);
  });

  it('reports a fresh token as not expiring soon', () => {
    writeTokens(pair(10 * 60_000));
    expect(accessTokenExpiringSoon()).toBe(false);
  });

  it('treats an unparseable expiry as not expiring soon rather than thrashing', () => {
    writeTokens({ ...pair(60_000), accessTokenExpiresAt: 'nonsense' });
    expect(accessTokenExpiringSoon()).toBe(false);
  });

  it('does not consider an absent session as expiring', () => {
    vi.useFakeTimers();
    expect(accessTokenExpiringSoon()).toBe(false);
    vi.useRealTimers();
  });
});
