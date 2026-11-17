import type { ProblemDetail, TokenPair } from './types';
import { clearTokens, readTokens, writeTokens } from '../auth/tokenStore';

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

/** An error carrying the server's problem detail, so the UI can show the real reason. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem: ProblemDetail) {
    const detail = problem.detail ?? problem.title ?? `Request failed with status ${status}`;
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code ?? 'UNKNOWN';
    this.detail = detail;
    this.fieldErrors = problem.errors ?? {};
  }
}

type Listener = () => void;
const sessionExpiredListeners = new Set<Listener>();

/** Register a callback invoked when a refresh fails and the session must be dropped. */
export function onSessionExpired(listener: Listener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

function notifySessionExpired(): void {
  for (const listener of sessionExpiredListeners) listener();
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** Set for the refresh call itself, so it does not recurse. */
  skipRefresh?: boolean;
  /** Set for public reads, so no Authorization header is attached. */
  anonymous?: boolean;
}

async function parseProblem(response: Response): Promise<ProblemDetail> {
  try {
    const data = (await response.json()) as ProblemDetail;
    return data ?? {};
  } catch {
    return {};
  }
}

let refreshInFlight: Promise<TokenPair | null> | null = null;

/**
 * Refresh the access token exactly once even if several requests 401 at the same
 * moment: the backend rotates the refresh token on every use, so two concurrent
 * refreshes would make the second present an already-consumed token and drop the
 * session. The in-flight promise is shared instead.
 */
async function refreshTokens(): Promise<TokenPair | null> {
  if (refreshInFlight) return refreshInFlight;
  const current = readTokens();
  if (!current) return null;

  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: current.refreshToken }),
      });
      if (!response.ok) {
        clearTokens();
        return null;
      }
      const result = (await response.json()) as { tokens: TokenPair };
      writeTokens(result.tokens);
      return result.tokens;
    } catch {
      clearTokens();
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, skipRefresh = false, anonymous = false } = options;

  const doFetch = async (token: string | null): Promise<Response> => {
    const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers };
    if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
    if (token) finalHeaders['Authorization'] = `Bearer ${token}`;
    return fetch(`${BASE_URL}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  };

  const tokens = readTokens();
  let response = await doFetch(anonymous ? null : (tokens?.accessToken ?? null));

  if (response.status === 401 && !anonymous && !skipRefresh && tokens) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      response = await doFetch(refreshed.accessToken);
    } else {
      notifySessionExpired();
    }
  }

  if (response.status === 204) {
    return undefined as T;
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseProblem(response));
  }

  // A 202 (resend, reset request) carries no body, and a proxy may strip a body
  // without the content-type saying so. Read the text once and only parse it when
  // there is something to parse, so an empty success is not mistaken for an error.
  const text = await response.text();
  if (text.length === 0) {
    return undefined as T;
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    return text as unknown as T;
  }
  return JSON.parse(text) as T;
}

export const api = {
  get: <T>(path: string, anonymous = false) => request<T>(path, { anonymous }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: 'POST', body, headers }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
