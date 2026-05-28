import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import RegisterPage from './RegisterPage';
import * as authApi from '../api/auth';
import * as cryptoService from '../crypto/cryptoService';
import * as cryptoUtils from '../crypto/utils';

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

function renderRegisterPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <AuthProvider>
        <RegisterPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.getKdfInfo).mockRejectedValue(new Error('401'));
    vi.mocked(authApi.getConfig).mockResolvedValue({
      code: 0, message: 'success',
      data: { kdf: { algorithm: 'pbkdf2-sha256', iterations: 600000 }, limits: { max_attachment_size_mb: 10, max_attachments_per_entry: 20 } },
    } as { code: number; message: string; data: { kdf: { algorithm: string; iterations: number }; limits: { max_attachment_size_mb: number; max_attachments_per_entry: number } } });
    vi.mocked(cryptoService.generateRandomBytes).mockReturnValue(new Uint8Array(16).fill(0x42));
    vi.mocked(cryptoUtils.arrayBufferToBase64).mockReturnValue('saltB64');
    vi.mocked(cryptoService.deriveKey).mockResolvedValue({} as CryptoKey);
    vi.mocked(cryptoService.generateDEK).mockResolvedValue({} as CryptoKey);
    vi.mocked(cryptoService.encryptDEK).mockResolvedValue({ encryptedPayload: 'encDek', iv: 'ivB64' });
    vi.mocked(cryptoService.deriveAuthKeyBytes).mockResolvedValue(new Uint8Array(32).buffer);
    vi.mocked(cryptoService.hashAuthKey).mockResolvedValue('hash123');
    vi.mocked(authApi.register).mockResolvedValue({
      code: 0, message: '注册成功', data: { user_id: 'uuid', created_at: '2026-01-01T00:00:00Z' },
    });
  });

  it('should render registration form', async () => {
    renderRegisterPage();
    await waitFor(() => {
      expect(screen.getByPlaceholderText(/3-32/)).toBeTruthy();
    });
    expect(screen.getByPlaceholderText(/至少 8 位/)).toBeTruthy();
    expect(screen.getByRole('button', { name: '注册' })).toBeTruthy();
  });

  it('should show error for invalid username', async () => {
    renderRegisterPage();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '注册' })).toBeTruthy();
    });

    const usernameInput = screen.getByPlaceholderText(/3-32/);
    const passwordInput = screen.getByPlaceholderText(/至少 8 位/);

    await userEvent.type(usernameInput, 'ab');
    await userEvent.type(passwordInput, 'Password1');
    await userEvent.click(screen.getByRole('button', { name: '注册' }));

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('should show error for weak password', async () => {
    renderRegisterPage();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '注册' })).toBeTruthy();
    });

    const usernameInput = screen.getByPlaceholderText(/3-32/);
    const passwordInput = screen.getByPlaceholderText(/至少 8 位/);

    await userEvent.type(usernameInput, 'alice');
    await userEvent.type(passwordInput, 'weak');
    await userEvent.click(screen.getByRole('button', { name: '注册' }));

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('should show recovery key after successful registration', async () => {
    renderRegisterPage();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: '注册' })).toBeTruthy();
    });

    const usernameInput = screen.getByPlaceholderText(/3-32/);
    const passwordInput = screen.getByPlaceholderText(/至少 8 位/);

    const confirmInput = screen.getByPlaceholderText('请再次输入密码');

    await userEvent.type(usernameInput, 'alice');
    await userEvent.type(passwordInput, 'Password1');
    await userEvent.type(confirmInput, 'Password1');
    await userEvent.click(screen.getByRole('button', { name: '注册' }));

    await waitFor(() => {
      expect(screen.getByText('保存你的恢复密钥')).toBeTruthy();
    });
  });
});
