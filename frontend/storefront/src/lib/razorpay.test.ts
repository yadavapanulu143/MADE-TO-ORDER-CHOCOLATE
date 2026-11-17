import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { loadRazorpay as LoadRazorpay } from './razorpay';

// The loader caches the in-flight promise for the tab's lifetime, so each test
// gets a fresh module instance rather than resetting module state through a
// test-only export.
let loadRazorpay: typeof LoadRazorpay;

class FakeRazorpay {
  options: unknown;
  constructor(options: unknown) {
    this.options = options;
  }
  open() {
    /* no-op */
  }
  on() {
    /* no-op */
  }
}

describe('loadRazorpay', () => {
  beforeEach(async () => {
    vi.resetModules();
    delete (window as { Razorpay?: unknown }).Razorpay;
    document.head.querySelectorAll('script').forEach((script) => script.remove());
    ({ loadRazorpay } = await import('./razorpay'));
  });

  it('reuses an already-loaded checkout script', async () => {
    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;

    await expect(loadRazorpay()).resolves.toBe(FakeRazorpay);
    expect(document.head.querySelectorAll('script').length).toBe(0);
  });

  it('injects the provider script and resolves once it loads', async () => {
    const promise = loadRazorpay();
    const script = document.head.querySelector('script');
    expect(script?.src).toContain('checkout.razorpay.com');

    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    script?.dispatchEvent(new Event('load'));

    await expect(promise).resolves.toBe(FakeRazorpay);
  });

  it('rejects when the script cannot be reached', async () => {
    const promise = loadRazorpay();
    document.head.querySelector('script')?.dispatchEvent(new Event('error'));

    await expect(promise).rejects.toThrow(/could not reach the payment provider/i);
  });

  it('rejects when the script loads but never defines the global', async () => {
    const promise = loadRazorpay();
    document.head.querySelector('script')?.dispatchEvent(new Event('load'));

    await expect(promise).rejects.toThrow(/failed to initialise/i);
  });

  it('shares one in-flight load across concurrent callers', async () => {
    const first = loadRazorpay();
    const second = loadRazorpay();
    expect(document.head.querySelectorAll('script').length).toBe(1);

    (window as { Razorpay?: unknown }).Razorpay = FakeRazorpay;
    document.head.querySelector('script')?.dispatchEvent(new Event('load'));

    await expect(first).resolves.toBe(FakeRazorpay);
    await expect(second).resolves.toBe(FakeRazorpay);
  });
});
