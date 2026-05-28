import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import type { ReactNode } from 'react';

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
  getKdfInfo: vi.fn(),
  setRecovery: vi.fn(),
  getRecovery: vi.fn(),
  resetPassword: vi.fn(),
  deleteRecovery: vi.fn(),
  deleteAccount: vi.fn(),
  getConfig: vi.fn(),
}));

vi.mock('../crypto/cryptoService', () => ({
  deriveAuthKeyBytes: vi.fn(),
  deriveKey: vi.fn(),
  generateDEK: vi.fn(),
  encryptDEK: vi.fn(),
  decryptDEK: vi.fn(),
  hashAuthKey: vi.fn(),
  generateRandomBytes: vi.fn(),
}));

vi.mock('../crypto/utils', () => ({
  arrayBufferToBase64: vi.fn(),
  base64ToArrayBuffer: vi.fn(),
  stringToArrayBuffer: vi.fn(),
  arrayBufferToString: vi.fn(),
  arrayBufferToHex: vi.fn(),
  hexToArrayBuffer: vi.fn(),
  concatArrayBuffers: vi.fn(),
}));

import * as authApi from '../api/auth';
import * as cryptoService from '../crypto/cryptoService';
import * as cryptoUtils from '../crypto/utils';

function TestConsumer() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="isLoading">{String(auth.isLoading)}</span>
      <span data-testid="isAuthenticated">{String(auth.isAuthenticated)}</span>
      <span data-testid="user">{auth.user ? auth.user.username : 'none'}</span>
    </div>
  );
}

function Wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.getConfig).mockResolvedValue({
      code: 0,
      message: 'success',
      data: { kdf: { algorithm: 'pbkdf2-sha256', iterations: 600000 }, limits: { max_attachment_size_mb: 10, max_attachments_per_entry: 20 } },
    });
  });

  describe('checkSession', () => {
    it('should finish loading when getKdfInfo succeeds', async () => {
      vi.mocked(authApi.getKdfInfo).mockResolvedValue({
        code: 0,
        message: 'success',
        data: { current: {}, recommended: {} },
      });

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });
      expect(screen.getByTestId('isAuthenticated').textContent).toBe('false');
    });

    it('should set isAuthenticated=false when getKdfInfo fails', async () => {
      vi.mocked(authApi.getKdfInfo).mockRejectedValue(new Error('401'));

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });
      expect(screen.getByTestId('isAuthenticated').textContent).toBe('false');
    });
  });

  describe('login', () => {
    it('should login successfully and set user with DEK', async () => {
      const mockSaltEnc = new Uint8Array(16).fill(0x01);
      const mockAuthKeyBytes = new Uint8Array(32).fill(0x02);
      const mockKek = {} as CryptoKey;
      const mockDek = {} as CryptoKey;

      vi.mocked(authApi.getRecovery).mockResolvedValue({
        code: 0,
        message: 'success',
        data: { salt_enc: 'AQEBAQEBAQEBAQEBAQEBAQ==' },
      });
      vi.mocked(cryptoUtils.base64ToArrayBuffer).mockReturnValue(mockSaltEnc.buffer);
      vi.mocked(cryptoService.deriveAuthKeyBytes).mockResolvedValue(mockAuthKeyBytes.buffer);
      vi.mocked(cryptoUtils.arrayBufferToBase64).mockReturnValue('authKeyB64');
      vi.mocked(authApi.login).mockResolvedValue({
        code: 0,
        message: '成功',
        data: { userId: 'user-1', encryptedDek: 'encDek', saltEnc: 'saltEnc', encryptedDekRecovery: '', kdfVersion: 1, kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 600000 }, hasRecovery: false },
      });
      vi.mocked(cryptoService.deriveKey).mockResolvedValue(mockKek);
      vi.mocked(cryptoService.decryptDEK).mockResolvedValue(mockDek);

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });

      let auth: ReturnType<typeof useAuth> | null = null;
      function LoginTrigger() {
        auth = useAuth();
        return null;
      }

      render(
        <AuthProvider>
          <LoginTrigger />
        </AuthProvider>,
      );

      await act(async () => {
        await auth!.login('alice', 'Password123');
      });

      expect(authApi.login).toHaveBeenCalledWith('alice', 'authKeyB64');
    });

    it('should throw on invalid credentials', async () => {
      vi.mocked(authApi.getRecovery).mockRejectedValue({ code: 404 });

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });

      let auth: ReturnType<typeof useAuth> | null = null;
      function LoginTrigger() {
        auth = useAuth();
        return null;
      }

      render(
        <AuthProvider>
          <LoginTrigger />
        </AuthProvider>,
      );

      await expect(act(async () => {
        await auth!.login('alice', 'wrong');
      })).rejects.toThrow('用户名或密码错误');
    });
  });

  describe('register', () => {
    it('should register and return recovery key', async () => {
      vi.mocked(cryptoService.generateRandomBytes).mockReturnValue(new Uint8Array(16).fill(0x42));
      vi.mocked(cryptoUtils.arrayBufferToBase64).mockReturnValue('saltB64');
      vi.mocked(cryptoService.deriveKey).mockResolvedValue({} as CryptoKey);
      vi.mocked(cryptoService.generateDEK).mockResolvedValue({} as CryptoKey);
      vi.mocked(cryptoService.encryptDEK).mockResolvedValue({ encryptedPayload: 'encDek', iv: 'ivB64' });
      vi.mocked(cryptoService.deriveAuthKeyBytes).mockResolvedValue(new Uint8Array(32).buffer);
      vi.mocked(cryptoService.hashAuthKey).mockResolvedValue('hash123');
      vi.mocked(authApi.register).mockResolvedValue({ code: 0, message: '注册成功', data: { user_id: 'uuid', created_at: '2026-01-01T00:00:00Z' } });

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });

      let auth: ReturnType<typeof useAuth> | null = null;
      function RegTrigger() {
        auth = useAuth();
        return null;
      }

      render(
        <AuthProvider>
          <RegTrigger />
        </AuthProvider>,
      );

      let result: { recoveryKey: string } | undefined;
      await act(async () => {
        result = await auth!.register('alice', 'Password123');
      });

      expect(result!.recoveryKey).toBeTruthy();
      expect(authApi.register).toHaveBeenCalled();
      const callArgs = vi.mocked(authApi.register).mock.calls[0][0];
      expect(callArgs.username).toBe('alice');
      expect(callArgs.authKey).toBe('saltB64');
    });
  });

  describe('logout', () => {
    it('should clear auth state', async () => {
      vi.mocked(authApi.getKdfInfo).mockResolvedValue({
        code: 0, message: 'success', data: { current: {}, recommended: {} },
      });
      vi.mocked(authApi.getRecovery).mockResolvedValue({
        code: 0, message: 'success',
        data: { salt_enc: 'AQEBAQEBAQEBAQEBAQEBAQ==' },
      });
      vi.mocked(cryptoUtils.base64ToArrayBuffer).mockReturnValue(new Uint8Array(16).buffer);
      vi.mocked(cryptoService.deriveAuthKeyBytes).mockResolvedValue(new Uint8Array(32).buffer);
      vi.mocked(cryptoUtils.arrayBufferToBase64).mockReturnValue('authKeyB64');
      vi.mocked(authApi.login).mockResolvedValue({
        code: 0, message: '成功',
        data: { userId: 'user-1', encryptedDek: 'encDek', saltEnc: 'saltEnc', encryptedDekRecovery: '', kdfVersion: 1, kdfParams: { algorithm: 'pbkdf2-sha256', iterations: 600000 }, hasRecovery: false },
      });
      vi.mocked(cryptoService.deriveKey).mockResolvedValue({} as CryptoKey);
      vi.mocked(cryptoService.decryptDEK).mockResolvedValue({} as CryptoKey);
      vi.mocked(authApi.logout).mockResolvedValue({ code: 0, message: '已登出', data: null });

      render(<TestConsumer />, { wrapper: Wrapper });

      await waitFor(() => {
        expect(screen.getByTestId('isLoading').textContent).toBe('false');
      });

      let auth: ReturnType<typeof useAuth> | null = null;
      function Trigger() {
        auth = useAuth();
        return null;
      }

      render(<AuthProvider><Trigger /></AuthProvider>);

      await act(async () => {
        await auth!.login('alice', 'Password123');
      });

      expect(auth?.isAuthenticated).toBe(true);

      await act(async () => {
        await auth!.logout();
      });

      expect(authApi.logout).toHaveBeenCalled();
      expect(auth?.isAuthenticated).toBe(false);
    });
  });
});
