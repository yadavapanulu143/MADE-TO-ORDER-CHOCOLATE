import { useEffect, useState } from 'react';
import { catalogApi } from '../api/endpoints';
import type { Settings } from '../api/types';

// Settings are the same for every page, so they are fetched once and cached for
// the tab's lifetime rather than re-requested on every navigation.
let cached: Settings | null = null;
let inFlight: Promise<Settings> | null = null;

/** Drop the cache. Tests call this between cases; production never does. */
export function resetSettingsCache(): void {
  cached = null;
  inFlight = null;
}

export function loadSettings(): Promise<Settings> {
  if (cached) return Promise.resolve(cached);
  if (!inFlight) {
    inFlight = catalogApi
      .getSettings()
      .then((settings) => {
        cached = settings;
        return settings;
      })
      .finally(() => {
        inFlight = null;
      });
  }
  return inFlight;
}

const EMPTY: Settings = {
  sellerName: null,
  contactEmail: null,
  contactPhone: null,
  contactAddress: null,
  privacyPolicy: null,
  termsAndConditions: null,
  shippingPolicy: null,
  refundPolicy: null,
  updatedAt: null,
};

export function useSettings(): Settings {
  const [settings, setSettings] = useState<Settings>(cached ?? EMPTY);
  useEffect(() => {
    let cancelled = false;
    loadSettings()
      .then((value) => {
        if (!cancelled) setSettings(value);
      })
      .catch(() => {
        /* a settings outage must not blank the page; defaults remain */
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return settings;
}
