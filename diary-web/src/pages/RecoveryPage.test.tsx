import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import RecoveryPage from './RecoveryPage';
import * as authApi from '../api/auth';

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

function renderRecoveryPage() {
  return render(
    <MemoryRouter initialEntries={['/recovery']}>
      <RecoveryPage />
    </MemoryRouter>,
  );
}

describe('RecoveryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render username step initially', async () => {
    renderRecoveryPage();
    expect(screen.getByPlaceholderText('请输入用户名')).toBeTruthy();
    expect(screen.getByRole('button', { name: '查找账号' })).toBeTruthy();
  });

  it('should show error for empty username', async () => {
    renderRecoveryPage();
    expect(screen.getByRole('button', { name: '查找账号' })).toBeTruthy();
  });

  it('should show user not found error', async () => {
    vi.mocked(authApi.getRecovery).mockRejectedValue({ code: 404 });

    renderRecoveryPage();

    const input = screen.getByPlaceholderText('请输入用户名');
    await userEvent.type(input, 'unknown');
    await userEvent.click(screen.getByRole('button', { name: '查找账号' }));

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toContain('用户不存在');
    });
  });

  it('should show error when no recovery info set', async () => {
    vi.mocked(authApi.getRecovery).mockResolvedValue({
      code: 0,
      message: 'success',
      data: {
        recovery_data: '',
        recovery_salt: '',
        salt_enc: 'salt',
        encrypted_dek_recovery: 'encDek',
        recovery_token: 'token',
      },
    });

    renderRecoveryPage();

    const input = screen.getByPlaceholderText('请输入用户名');
    await userEvent.type(input, 'alice');
    await userEvent.click(screen.getByRole('button', { name: '查找账号' }));

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toContain('未设置恢复口令托管');
    });
  });

  it('should advance to recovery phrase step on success', async () => {
    vi.mocked(authApi.getRecovery).mockResolvedValue({
      code: 0,
      message: 'success',
      data: {
        recovery_data: 'cipher:iv',
        recovery_salt: 'saltB64',
        salt_enc: 'saltEnc',
        encrypted_dek_recovery: 'dekCipher:dekIv',
        recovery_token: 'token123',
      },
    });

    renderRecoveryPage();

    const input = screen.getByPlaceholderText('请输入用户名');
    await userEvent.type(input, 'alice');
    await userEvent.click(screen.getByRole('button', { name: '查找账号' }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('请输入你设置的恢复口令')).toBeTruthy();
    });
  });

  it('should have a link back to login', () => {
    renderRecoveryPage();
    expect(screen.getByText('返回登录')).toBeTruthy();
  });
});
