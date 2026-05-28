import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './client';

describe('api/client', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mock.restore();
  });

  describe('base configuration', () => {
    it('should have correct baseURL', () => {
      expect(apiClient.defaults.baseURL).toBe('/api/v1');
    });

    it('should have withCredentials enabled', () => {
      expect(apiClient.defaults.withCredentials).toBe(true);
    });

    it('should not set a default Content-Type header', () => {
      const headers = apiClient.defaults.headers;
      expect(headers['Content-Type']).toBeUndefined();
    });
  });

  describe('response interceptor', () => {
    it('should return response data on success', async () => {
      mock.onGet('/test').reply(200, { code: 0, message: 'success', data: { id: 1 } });

      const result = await apiClient.get('/test');
      expect(result).toEqual({ code: 0, message: 'success', data: { id: 1 } });
    });

    it('should dispatch auth:session-expired event on 401', async () => {
      const handler = vi.fn();
      window.addEventListener('auth:session-expired', handler);

      mock.onGet('/test').reply(401, { code: 401, message: '未认证' });

      try {
        await apiClient.get('/test');
      } catch {
        // expected
      }

      expect(handler).toHaveBeenCalled();
      window.removeEventListener('auth:session-expired', handler);
    });

    it('should throw error with serverVersion on 409 conflict', async () => {
      mock.onPut('/entries/1').reply(409, {
        code: 409,
        message: '版本冲突',
        data: { version: 3, updated_at: '2026-05-27T12:05:00Z' },
      });

      try {
        await apiClient.put('/entries/1', {});
        expect.fail('should have thrown');
      } catch (error: unknown) {
        const e = error as { code: number; serverVersion: number; serverUpdatedAt: string };
        expect(e.code).toBe(409);
        expect(e.serverVersion).toBe(3);
        expect(e.serverUpdatedAt).toBe('2026-05-27T12:05:00Z');
      }
    });

    it('should throw error with retryAfter on 429', async () => {
      mock.onGet('/test').reply(
        429,
        { code: 429, message: '请求过于频繁' },
        { 'retry-after': '120' },
      );

      try {
        await apiClient.get('/test');
        expect.fail('should have thrown');
      } catch (error: unknown) {
        const e = error as { code: number; retryAfter: number };
        expect(e.code).toBe(429);
        expect(e.retryAfter).toBe(120);
      }
    });

    it('should throw error on general failure', async () => {
      mock.onGet('/test').reply(500, { code: 500, message: '服务器错误' });

      try {
        await apiClient.get('/test');
        expect.fail('should have thrown');
      } catch (error: unknown) {
        const e = error as Error;
        expect(e.message).toBe('服务器错误');
      }
    });

    it('should throw network error when no response', async () => {
      mock.onGet('/test').networkError();

      try {
        await apiClient.get('/test');
        expect.fail('should have thrown');
      } catch (error: unknown) {
        const e = error as Error;
        expect(e.message).toBe('网络错误，请检查网络连接');
      }
    });
  });
});
