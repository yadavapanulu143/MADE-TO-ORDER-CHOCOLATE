import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { resetSettingsCache } from '../lib/useSettings';

afterEach(() => {
  cleanup();
  window.localStorage.clear();
  // Settings are cached for the tab's lifetime; a test that stubs them must not
  // leak that stub into the next test.
  resetSettingsCache();
  vi.restoreAllMocks();
});

// jsdom has no crypto.randomUUID; the app uses it for idempotency keys.
if (typeof globalThis.crypto === 'object' && !globalThis.crypto.randomUUID) {
  Object.defineProperty(globalThis.crypto, 'randomUUID', {
    value: () => '00000000-0000-4000-8000-000000000000',
    configurable: true,
  });
}
