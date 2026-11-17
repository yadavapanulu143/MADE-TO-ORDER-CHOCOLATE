import { useCallback, useEffect, useRef, useState } from 'react';

interface AsyncState<T> {
  data: T | null;
  error: unknown;
  loading: boolean;
  reload: () => void;
}

/**
 * Run an async loader and expose its data, error and a reload. The loader identity
 * is the dependency, so a caller wraps it in useCallback; the latest result wins,
 * so a slow earlier request cannot overwrite a newer one.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [nonce, setNonce] = useState(0);
  const latest = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(loader, deps);

  useEffect(() => {
    const ticket = ++latest.current;
    setLoading(true);
    setError(null);
    run()
      .then((result) => {
        if (ticket === latest.current) setData(result);
      })
      .catch((caught) => {
        if (ticket === latest.current) setError(caught);
      })
      .finally(() => {
        if (ticket === latest.current) setLoading(false);
      });
  }, [run, nonce]);

  const reload = useCallback(() => setNonce((value) => value + 1), []);

  return { data, error, loading, reload };
}
