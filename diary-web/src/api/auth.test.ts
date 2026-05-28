import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import apiClient from './client';
import {
  register,
  login,
  logout,
  changePassword,
  getKdfInfo,
  setRecovery,
  getRecovery,
  resetPassword,
  deleteRecovery,
  deleteAccount,
} from './auth';

describe('api/auth', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    mock = new MockAdapter(apiClient);
  });

  afterEach(() => {
    mock.restore();
  });

  describe('register', () => {
    it('should POST to /auth/register with correct payload', async () => {
      const params = {
        username: 'alice',
        authKey: 'hash123',
        saltAuth: 'saltA',
        encryptedDek: 'encDek',
        encryptedDekRecovery: 'encDekRec',
        saltEnc: 'saltE',
        kdfVersion: 1,
        kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 600000 },
      };

      mock.onPost('/auth/register').reply(200, {
        code: 0,
        message: '注册成功',
        data: { user_id: 'uuid-1', created_at: '2026-05-27T12:00:00Z' },
      });

      const result = await register(params);
      expect(result.code).toBe(0);
      expect(result.data.user_id).toBe('uuid-1');

      const req = JSON.parse(mock.history.post[0].data);
      expect(req.username).toBe('alice');
      expect(req.authKey).toBe('hash123');
      expect(req.kdfParams.iterations).toBe(600000);
    });
  });

  describe('login', () => {
    it('should POST to /auth/login with correct payload', async () => {
      mock.onPost('/auth/login').reply(200, {
        code: 0,
        message: '登录成功',
        data: {
          userId: 'uuid-1',
          encryptedDek: 'encDek',
          encryptedDekRecovery: 'encDekRec',
          saltEnc: 'saltE',
          kdfVersion: 1,
          kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 600000 },
          hasRecovery: false,
        },
      });

      const result = await login('alice', 'authKeyRaw');
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.post[0].data);
      expect(req.username).toBe('alice');
      expect(req.authKey).toBe('authKeyRaw');
    });
  });

  describe('logout', () => {
    it('should POST to /auth/logout', async () => {
      mock.onPost('/auth/logout').reply(200, { code: 0, message: '已登出', data: null });
      const result = await logout();
      expect(result.code).toBe(0);
    });
  });

  describe('changePassword', () => {
    it('should PUT to /auth/password with correct payload', async () => {
      mock.onPut('/auth/password').reply(200, {
        code: 0,
        message: '密码已修改，请重新登录',
        data: null,
      });

      const result = await changePassword(
        'oldAuthKey',
        'newHash',
        'newDek',
        'newDekRec',
        'newSalt',
        { algorithm: 'pbkdf2-sha256', iterations: 800000 },
      );
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.put[0].data);
      expect(req.oldAuthKey).toBe('oldAuthKey');
      expect(req.newAuthKeyHash).toBe('newHash');
      expect(req.newKdfParams.iterations).toBe(800000);
    });
  });

  describe('getKdfInfo', () => {
    it('should GET /auth/kdf-info', async () => {
      mock.onGet('/auth/kdf-info').reply(200, {
        code: 0,
        message: 'success',
        data: {
          current: { kdfVersion: 1, kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 600000 } },
          recommended: { kdfVersion: 1, kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 800000 } },
        },
      });

      const result = await getKdfInfo();
      expect(result.code).toBe(0);
      expect(result.data.recommended.kdfParams.iterations).toBe(800000);
    });
  });

  describe('setRecovery', () => {
    it('should PUT to /auth/recovery', async () => {
      mock.onPut('/auth/recovery').reply(200, {
        code: 0,
        message: '托管信息已设置',
        data: null,
      });

      const result = await setRecovery('recoveryDataB64', 'recoverySaltB64');
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.put[0].data);
      expect(req.recoveryData).toBe('recoveryDataB64');
      expect(req.recoverySalt).toBe('recoverySaltB64');
    });
  });

  describe('getRecovery', () => {
    it('should GET /auth/recovery with username param', async () => {
      mock.onGet('/auth/recovery').reply(200, {
        code: 0,
        message: 'success',
        data: {
          recovery_data: 'recData',
          recovery_salt: 'recSalt',
          salt_enc: 'saltEnc',
          encrypted_dek_recovery: 'encDekRec',
          recovery_token: 'token123',
        },
      });

      const result = await getRecovery('alice');
      expect(result.code).toBe(0);
      expect(result.data.recovery_token).toBe('token123');

      const params = mock.history.get[0].params;
      expect(params.username).toBe('alice');
    });
  });

  describe('resetPassword', () => {
    it('should POST to /auth/recovery/reset', async () => {
      mock.onPost('/auth/recovery/reset').reply(200, {
        code: 0,
        message: '密码已重置，请使用新密码登录',
        data: null,
      });

      const result = await resetPassword(
        'alice',
        'token123',
        'newHash',
        'newDek',
        'newDekRec',
        'newSalt',
        { algorithm: 'pbkdf2-sha256', iterations: 800000 },
      );
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.post[0].data);
      expect(req.username).toBe('alice');
      expect(req.recoveryToken).toBe('token123');
    });
  });

  describe('deleteRecovery', () => {
    it('should DELETE /auth/recovery with authKey in body', async () => {
      mock.onDelete('/auth/recovery').reply(200, {
        code: 0,
        message: '托管信息已删除',
        data: null,
      });

      const result = await deleteRecovery('authKeyRaw');
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.delete[0].data);
      expect(req.authKey).toBe('authKeyRaw');
    });
  });

  describe('deleteAccount', () => {
    it('should DELETE /auth/account with authKey in body', async () => {
      mock.onDelete('/auth/account').reply(200, {
        code: 0,
        message: '账户已注销',
        data: null,
      });

      const result = await deleteAccount('authKeyRaw');
      expect(result.code).toBe(0);

      const req = JSON.parse(mock.history.delete[0].data);
      expect(req.authKey).toBe('authKeyRaw');
    });
  });
});
