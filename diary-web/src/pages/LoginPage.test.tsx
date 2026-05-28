import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import LoginPage from './LoginPage';
import * as authApi from '../api/auth';
import * as loginDelay from '../utils/loginDelay';

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

vi.mock('../utils/loginDelay', () => ({
  recordLoginFailure: vi.fn(() => Promise.resolve()),
  getLoginCooldownSeconds: vi.fn(() => Promise.resolve(0)),
  resetLoginFailures: vi.fn(() => Promise.resolve()),
  getLoginFailureCount: vi.fn(() => Promise.resolve(0)),
}));

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(authApi.getKdfInfo).mockRejectedValue(new Error('401'));
    vi.mocked(authApi.getConfig).mockResolvedValue({
      code: 0, message: 'success',
      data: { kdf: { algorithm: 'pbkdf2-sha256', iterations: 600000 }, limits: { max_attachment_size_mb: 10, max_attachments_per_entry: 20 } },
    });
  });

  it('should render login form', async () => {
    renderLoginPage();
    await waitFor(() => {
      expect(screen.getByPlaceholderText('请输入用户名')).toBeTruthy();
    });
    expect(screen.getByPlaceholderText('请输入密码')).toBeTruthy();
    expect(screen.getByRole('button', { name: '登录' })).toBeTruthy();
  });

  it('should show error for empty fields', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '登录' })).toBeTruthy();
    });

    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('should disable button when loading', async () => {
    vi.mocked(authApi.getRecovery).mockImplementation(
      () => new Promise(() => {}),
    );

    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '登录' })).toBeTruthy();
    });

    const usernameInput = screen.getByPlaceholderText('请输入用户名');
    const passwordInput = screen.getByPlaceholderText('请输入密码');

    await userEvent.type(usernameInput, 'alice');
    await userEvent.type(passwordInput, 'Password1');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(screen.getByRole('button').textContent).toBe('登录中...');
  });

  it('should show cooldown message when blocked', async () => {
    vi.mocked(loginDelay.getLoginCooldownSeconds).mockResolvedValue(120);

    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByRole('status')).toBeTruthy();
    });

    expect(screen.getByRole('status').textContent).toContain('2:00');
  });

  it('should have links to register and recovery', async () => {
    renderLoginPage();

    await waitFor(() => {
      expect(screen.getByText('注册账号')).toBeTruthy();
    });

    expect(screen.getByText('忘记密码？')).toBeTruthy();
  });
});
