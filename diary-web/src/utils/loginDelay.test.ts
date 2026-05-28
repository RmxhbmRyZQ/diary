import { describe, it, expect, beforeEach, vi } from 'vitest';

const fakeStore = new Map<string, unknown>();

vi.mock('../db/index', () => ({
  getDB: () =>
    Promise.resolve({
      get: (_store: string, key: string) => fakeStore.get(key) ?? null,
      put: (_store: string, value: { key: string }) => {
        fakeStore.set(value.key, value);
        return Promise.resolve();
      },
      delete: (_store: string, key: string) => {
        fakeStore.delete(key);
        return Promise.resolve();
      },
    }),
}));

import {
  resetLoginFailures,
  recordLoginFailure,
  getLoginCooldownSeconds,
  getLoginFailureCount,
} from './loginDelay';

describe('loginDelay', () => {
  beforeEach(() => {
    fakeStore.clear();
    vi.useRealTimers();
  });

  describe('recordLoginFailure', () => {
    it('should increment failure count', async () => {
      await recordLoginFailure();
      expect(await getLoginFailureCount()).toBe(1);

      await recordLoginFailure();
      expect(await getLoginFailureCount()).toBe(2);
    });
  });

  describe('resetLoginFailures', () => {
    it('should clear all records', async () => {
      await recordLoginFailure();
      await recordLoginFailure();
      await resetLoginFailures();
      expect(await getLoginFailureCount()).toBe(0);
    });
  });

  describe('getLoginCooldownSeconds', () => {
    it('should return 0 when failures < 5', async () => {
      for (let i = 0; i < 4; i++) {
        await recordLoginFailure();
      }
      expect(await getLoginCooldownSeconds()).toBe(0);
    });

    it('should return cooldown seconds for 5th failure', async () => {
      vi.useFakeTimers();
      const now = new Date('2026-05-27T12:00:00Z').getTime();
      vi.setSystemTime(now);

      for (let i = 0; i < 5; i++) {
        await recordLoginFailure();
      }

      expect(await getLoginCooldownSeconds()).toBe(60);
    });

    it('should return increased cooldown after 6th failure', async () => {
      vi.useFakeTimers();
      const now = new Date('2026-05-27T12:00:00Z').getTime();
      vi.setSystemTime(now);

      for (let i = 0; i < 6; i++) {
        await recordLoginFailure();
      }

      expect(await getLoginCooldownSeconds()).toBe(120);
    });

    it('should return 0 after cooldown expires', async () => {
      vi.useFakeTimers();
      const now = new Date('2026-05-27T12:00:00Z').getTime();
      vi.setSystemTime(now);

      for (let i = 0; i < 5; i++) {
        await recordLoginFailure();
      }

      vi.advanceTimersByTime(61 * 1000);
      expect(await getLoginCooldownSeconds()).toBe(0);
    });

    it('should countdown remaining seconds', async () => {
      vi.useFakeTimers();
      const now = new Date('2026-05-27T12:00:00Z').getTime();
      vi.setSystemTime(now);

      for (let i = 0; i < 5; i++) {
        await recordLoginFailure();
      }

      vi.advanceTimersByTime(30 * 1000);
      const remaining = await getLoginCooldownSeconds();
      expect(remaining).toBeGreaterThan(0);
      expect(remaining).toBeLessThanOrEqual(30);
    });
  });
});
