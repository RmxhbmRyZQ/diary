import { createContext, useContext, useState, useCallback, useEffect, useRef, type ReactNode } from 'react';
import {
  deriveAuthKeyBytes,
  deriveKey,
  generateDEK,
  encryptDEK,
  decryptDEK,
  generateRandomBytes,
  type KdfParams,
} from '../crypto/cryptoService';
import { arrayBufferToBase64, base64ToArrayBuffer } from '../crypto/utils';
import * as authApi from '../api/auth';
import { clearAllData } from '../db';

const SESSION_TTL_MS = 30 * 60 * 1000; // 30 minutes

interface SessionKeyData {
  wrappedDek: string;
  dekIv: string;
  sessionKey: string;
  userId: string;
  username: string;
  timestamp: number;
}

async function persistSession(dek: CryptoKey, user: User): Promise<void> {
  const sessionKeyBytes = generateRandomBytes(32);
  const sessionKey = await crypto.subtle.importKey(
    'raw',
    sessionKeyBytes,
    { name: 'AES-GCM', length: 256 },
    true,
    ['wrapKey', 'unwrapKey'],
  );

  const iv = generateRandomBytes(12);
  const wrappedDek = await crypto.subtle.wrapKey(
    'raw',
    dek,
    sessionKey,
    { name: 'AES-GCM', iv },
  );

  const data: SessionKeyData = {
    wrappedDek: arrayBufferToBase64(wrappedDek),
    dekIv: arrayBufferToBase64(iv),
    sessionKey: arrayBufferToBase64(sessionKeyBytes),
    userId: user.userId,
    username: user.username,
    timestamp: Date.now(),
  };

  sessionStorage.setItem('sessionKeyData', JSON.stringify(data));
}

async function restoreSession(): Promise<{ dek: CryptoKey; user: User } | null> {
  const raw = sessionStorage.getItem('sessionKeyData');
  if (!raw) return null;

  try {
    const data: SessionKeyData = JSON.parse(raw);

    if (Date.now() - data.timestamp > SESSION_TTL_MS) {
      sessionStorage.removeItem('sessionKeyData');
      return null;
    }

    // refresh timestamp
    data.timestamp = Date.now();
    sessionStorage.setItem('sessionKeyData', JSON.stringify(data));

    const sessionKeyBytes = new Uint8Array(base64ToArrayBuffer(data.sessionKey));
    const sessionKey = await crypto.subtle.importKey(
      'raw',
      sessionKeyBytes,
      { name: 'AES-GCM', length: 256 },
      false,
      ['unwrapKey'],
    );

    const iv = new Uint8Array(base64ToArrayBuffer(data.dekIv));
    const wrappedDek = base64ToArrayBuffer(data.wrappedDek);

    const dek = await crypto.subtle.unwrapKey(
      'raw',
      wrappedDek,
      sessionKey,
      { name: 'AES-GCM', iv },
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt'],
    );

    return {
      dek,
      user: { userId: data.userId, username: data.username },
    };
  } catch {
    sessionStorage.removeItem('sessionKeyData');
    return null;
  }
}

function clearSessionData(): void {
  sessionStorage.removeItem('sessionKeyData');
  sessionStorage.removeItem('unlockUsername');
}

export interface User {
  userId: string;
  username: string;
}

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  needsUnlock: boolean;
  dek: CryptoKey | null;
  kdfIterations: number;
  needsKdfUpgrade: boolean;
}

interface AuthContextType extends AuthState {
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  changePassword: (oldPassword: string, newPassword: string) => Promise<void>;
  checkSession: () => Promise<void>;
  unlock: (password: string) => Promise<void>;
  dismissKdfUpgrade: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

const KDF_ALGORITHM = 'pbkdf2-sha256';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
    needsUnlock: false,
    dek: null,
    kdfIterations: 600000,
    needsKdfUpgrade: false,
  });

  const kdfIterationsRef = useRef(600000);
  const isAuthenticatedRef = useRef(false);

  useEffect(() => {
    kdfIterationsRef.current = state.kdfIterations;
  }, [state.kdfIterations]);

  useEffect(() => {
    isAuthenticatedRef.current = state.isAuthenticated;
  }, [state.isAuthenticated]);

  const checkSession = useCallback(async () => {
    try {
      // 1. Try to restore from sessionStorage (same tab, within 30 min)
      const sessionResult = await restoreSession();
      if (sessionResult) {
        setState((prev) => ({
          ...prev,
          isLoading: false,
          isAuthenticated: true,
          needsUnlock: false,
          user: sessionResult.user,
          dek: sessionResult.dek,
        }));
        return;
      }

      // 2. Check server session cookie
      const result = await authApi.getKdfInfo();
      if (result.code === 0) {
        const unlockUsername = sessionStorage.getItem('unlockUsername');
        if (unlockUsername) {
          setState((prev) => ({
            ...prev,
            isLoading: false,
            needsUnlock: true,
            user: { userId: '', username: unlockUsername },
          }));
        } else {
          setState((prev) => ({ ...prev, isLoading: false }));
        }
      }
    } catch {
      clearSessionData();
      setState((prev) => ({ ...prev, isAuthenticated: false, isLoading: false, needsUnlock: false }));
    }
  }, []);

  useEffect(() => {
    checkSession();
  }, [checkSession]);

  useEffect(() => {
    authApi.getConfig().then((result) => {
      const iterations = Number(result.data?.kdf?.iterations);
      if (iterations > 0) {
        setState((prev) => ({ ...prev, kdfIterations: iterations }));
      }
    }).catch(() => {
      // use default 600000 if config fetch fails
    });
  }, []);

  useEffect(() => {
    function handleSessionExpired(e: Event) {
      clearSessionData();
      setState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        needsUnlock: false,
        dek: null,
      });
      if (isAuthenticatedRef.current) {
        const detail = (e as CustomEvent).detail;
        const message = detail?.message || '您的账号在另一设备登录，当前会话已失效，请重新登录';
        window.alert(message);
      }
    }
    window.addEventListener('auth:session-expired', handleSessionExpired);
    return () => window.removeEventListener('auth:session-expired', handleSessionExpired);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    let saltEnc: Uint8Array;

    try {
      const recoveryResult = await authApi.getRecovery(username);
      const saltEncField = recoveryResult.data?.salt_enc;
      if (saltEncField) {
        saltEnc = new Uint8Array(base64ToArrayBuffer(saltEncField));
      } else {
        throw new Error('无法获取用户加密参数');
      }
    } catch (err: unknown) {
      const e = err as { code?: number };
      if (e.code === 404) {
        throw new Error('用户名或密码错误');
      }
      throw new Error('用户名或密码错误');
    }

    const iterations = Number(kdfIterationsRef.current) || 600000;
    const authKeyBytes = await deriveAuthKeyBytes(password, saltEnc, iterations);
    const authKey = arrayBufferToBase64(authKeyBytes);

    const result = await authApi.login(username, authKey);
    const data = result.data as authApi.LoginResponse;

    const kek = await deriveKey(password, saltEnc, iterations, 'encrypt');

    let dek: CryptoKey;
    try {
      const parts = data.encryptedDek.split(':');
      const encryptedDekPart = parts[0];
      const dekIv = parts.length > 1 ? parts[1] : data.saltEnc;
      dek = await decryptDEK(encryptedDekPart, kek, dekIv);
    } catch {
      throw new Error('密钥解密失败，请确认密码正确');
    }

    const userIterations = data.kdfParams?.iterations ?? 600000;
    const serverIterations = kdfIterationsRef.current;

    const user: User = { userId: data.userId, username };
    try {
      await persistSession(dek, user);
    } catch {
      // non-critical: login succeeds even if session persistence fails
    }
    sessionStorage.setItem('unlockUsername', username);
    setState({
      user,
      isAuthenticated: true,
      isLoading: false,
      needsUnlock: false,
      dek,
      needsKdfUpgrade: userIterations < serverIterations,
    });
  }, []);

  const register = useCallback(async (username: string, password: string) => {
    const salt = generateRandomBytes(16);
    const saltB64 = arrayBufferToBase64(salt);

    const iterations = Number(kdfIterationsRef.current) || 600000;
    const kek = await deriveKey(password, salt, iterations, 'encrypt');
    const dek = await generateDEK();
    const { encryptedPayload: encryptedDek, iv: dekIv } = await encryptDEK(dek, kek);
    const encryptedDekWithIv = `${encryptedDek}:${dekIv}`;

    const authKeyBytes = await deriveAuthKeyBytes(password, salt, iterations);
    const authKey = arrayBufferToBase64(authKeyBytes);

    await authApi.register({
      username,
      authKey,
      saltAuth: saltB64,
      encryptedDek: encryptedDekWithIv,
      saltEnc: saltB64,
      kdfVersion: 1,
      kdfParams: { algorithm: KDF_ALGORITHM, iterations },
    });

    // Auto-login after registration so recovery setup can work
    const loginResult = await authApi.login(username, authKey);
    const loginData = loginResult.data as authApi.LoginResponse;

    const user: User = { userId: loginData.userId, username };
    try {
      await persistSession(dek, user);
    } catch {
      // non-critical
    }
    sessionStorage.setItem('unlockUsername', username);
    setState({
      user,
      isAuthenticated: true,
      isLoading: false,
      needsUnlock: false,
      dek,
      needsKdfUpgrade: false,
    });
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // 即使 logout API 失败也要清除本地状态
    }
    clearSessionData();
    setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      needsUnlock: false,
      dek: null,
    });
    try {
      await clearAllData();
    } catch {
      // 清除缓存失败不阻塞注销
    }
  }, []);

  const dismissKdfUpgrade = useCallback(() => {
    setState((prev) => ({ ...prev, needsKdfUpgrade: false }));
  }, []);

  const unlock = useCallback(async (password: string) => {
    if (!state.user?.username) {
      throw new Error('无法解锁：缺少用户信息');
    }
    await login(state.user.username, password);
  }, [state.user, login]);

  const changePassword = useCallback(async (oldPassword: string, newPassword: string) => {
    if (!state.user || !state.dek) {
      throw new Error('未登录');
    }

    const recoveryResult = await authApi.getRecovery(state.user.username);
    const saltEncB64 = recoveryResult.data?.salt_enc;
    if (!saltEncB64) {
      throw new Error('无法获取加密参数');
    }
    const saltEnc = new Uint8Array(base64ToArrayBuffer(saltEncB64));

    const iterations = Number(kdfIterationsRef.current) || 600000;

    const oldAuthKeyBytes = await deriveAuthKeyBytes(oldPassword, saltEnc, iterations);
    const oldAuthKey = arrayBufferToBase64(oldAuthKeyBytes);

    const newAuthKeyBytes = await deriveAuthKeyBytes(newPassword, saltEnc, iterations);
    const newAuthKey = arrayBufferToBase64(newAuthKeyBytes);

    const newKek = await deriveKey(newPassword, saltEnc, iterations, 'encrypt');
    const { encryptedPayload: newEncryptedDek, iv: newDekIv } = await encryptDEK(state.dek, newKek);
    const newEncryptedDekWithIv = `${newEncryptedDek}:${newDekIv}`;

    await authApi.changePassword(
      oldAuthKey,
      newAuthKey,
      newEncryptedDekWithIv,
      arrayBufferToBase64(saltEnc),
      { algorithm: KDF_ALGORITHM, iterations },
    );

    clearSessionData();
    setState({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      dek: null,
    });
  }, [state.user, state.dek]);

  return (
    <AuthContext.Provider value={{ ...state, login, register, logout, changePassword, checkSession, unlock, dismissKdfUpgrade }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
