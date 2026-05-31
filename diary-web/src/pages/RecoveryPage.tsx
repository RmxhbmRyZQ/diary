import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PasswordStrength from '../components/ui/PasswordStrength';
import {
  deriveKey,
  encryptDEK,
  deriveAuthKeyBytes,
  generateRandomBytes,
} from '../crypto/cryptoService';
import { arrayBufferToBase64, base64ToArrayBuffer } from '../crypto/utils';
import { getRecovery, resetPassword } from '../api/auth';

type Step = 'username' | 'recovery-phrase' | 'new-password';

const KDF_PARAMS = { algorithm: 'pbkdf2-sha256', iterations: 600000 };

function parseEncryptedWithIv(data: string): { ciphertext: string; iv: string } {
  const parts = data.split(':');
  return {
    ciphertext: parts[0],
    iv: parts.length > 1 ? parts[1] : '',
  };
}

export default function RecoveryPage() {
  const navigate = useNavigate();

  const [step, setStep] = useState<Step>('username');
  const [username, setUsername] = useState('');
  const [recoveryData, setRecoveryData] = useState('');
  const [recoverySalt, setRecoverySalt] = useState('');
  const [recoveryPhrase, setRecoveryPhrase] = useState('');
  const [challenge, setChallenge] = useState('');
  const [challengeIv, setChallengeIv] = useState('');
  const [encryptedChallenge, setEncryptedChallenge] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [dek, setDek] = useState<CryptoKey | null>(null);

  async function handleFetchRecovery(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await getRecovery(username.trim());
      const data = result.data;

      if (!data.recovery_data || !data.recovery_salt) {
        setError('该用户未设置恢复口令托管');
        setLoading(false);
        return;
      }

      setRecoveryData(data.recovery_data);
      setRecoverySalt(data.recovery_salt);
      setChallenge(data.challenge || '');
      setChallengeIv(data.challenge_iv || '');
      setStep('recovery-phrase');
    } catch (err: unknown) {
      const e = err as { code?: number; message?: string };
      setError(e.message || '获取恢复信息失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }

  async function handleDecryptRecovery(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const recoverySaltBytes = new Uint8Array(base64ToArrayBuffer(recoverySalt));
      const recoveryKek = await deriveKey(recoveryPhrase, recoverySaltBytes, KDF_PARAMS.iterations, 'encrypt');

      const { ciphertext: recCipher, iv: recIv } = parseEncryptedWithIv(recoveryData);
      if (!recIv) throw new Error('缺少 IV');

      const decryptedDekRaw = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: new Uint8Array(base64ToArrayBuffer(recIv)) },
        recoveryKek,
        base64ToArrayBuffer(recCipher),
      );

      const recoveredDek = await crypto.subtle.importKey(
        'raw',
        new Uint8Array(decryptedDekRaw),
        { name: 'AES-GCM', length: 256 },
        true,
        ['encrypt', 'decrypt'],
      );

      if (challenge && challengeIv) {
        const challengeBytes = new Uint8Array(base64ToArrayBuffer(challenge));
        const encChallenge = await crypto.subtle.encrypt(
          { name: 'AES-GCM', iv: new Uint8Array(base64ToArrayBuffer(challengeIv)) },
          recoveryKek,
          challengeBytes,
        );
        setEncryptedChallenge(`${arrayBufferToBase64(new Uint8Array(encChallenge).buffer)}:${challengeIv}`);
      }

      setDek(recoveredDek);
      setStep('new-password');
    } catch {
      setError('恢复口令错误，请重试');
    } finally {
      setLoading(false);
    }
  }

  function validatePassword(pw: string): string | null {
    if (pw.length < 8) return '密码至少 8 位';
    if (!/[A-Z]/.test(pw)) return '密码需包含大写字母';
    if (!/[a-z]/.test(pw)) return '密码需包含小写字母';
    if (!/[0-9]/.test(pw)) return '密码需包含数字';
    return null;
  }

  async function handleResetPassword(e: FormEvent) {
    e.preventDefault();
    setError('');

    const pwError = validatePassword(newPassword);
    if (pwError) {
      setError(pwError);
      return;
    }

    if (!dek) {
      setError('恢复信息已过期，请重新开始');
      return;
    }

    setLoading(true);

    try {
      const salt = generateRandomBytes(16);
      const saltB64 = arrayBufferToBase64(salt);

      const newAuthKeyBytes = await deriveAuthKeyBytes(newPassword, salt, KDF_PARAMS.iterations);
      const newAuthKey = arrayBufferToBase64(newAuthKeyBytes);

      const newKek = await deriveKey(newPassword, salt, KDF_PARAMS.iterations, 'encrypt');
      const { encryptedPayload: newEncryptedDek, iv: newDekIv } = await encryptDEK(dek, newKek);
      const newEncryptedDekWithIv = `${newEncryptedDek}:${newDekIv}`;

      await resetPassword(
        username.trim(),
        newAuthKey,
        newEncryptedDekWithIv,
        saltB64,
        KDF_PARAMS,
        encryptedChallenge,
      );

      navigate('/login', { replace: true, state: { recovered: true } });
    } catch (err: unknown) {
      const e = err as { code?: number; message?: string };
      if (e.code === 401) {
        setError('恢复口令验证失败，请重新开始');
        setStep('username');
      } else {
        setError(e.message || '重置失败，请重试');
      }
    } finally {
      setLoading(false);
    }
  }

  if (step === 'new-password') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
        <div className="w-full max-w-sm card space-y-4">
          <h2 className="text-xl font-bold text-gray-800 text-center">设置新密码</h2>
          <p className="text-sm text-gray-500 text-center">恢复密钥验证成功，请设置新密码</p>

          <form onSubmit={handleResetPassword} className="space-y-4">
            <div>
              <label htmlFor="new-pw" className="block text-sm font-medium text-gray-600 mb-1">新密码</label>
              <input
                id="new-pw"
                type="password"
                className="input-field"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="至少 8 位，含大小写字母和数字"
                autoComplete="new-password"
                disabled={loading}
              />
              <PasswordStrength password={newPassword} />
            </div>

            {error && <p className="text-red-500 text-sm text-center" role="alert">{error}</p>}

            <button type="submit" className="btn-primary w-full" disabled={loading}>
              {loading ? '重置中...' : '重置密码'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  if (step === 'recovery-phrase') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
        <div className="w-full max-w-sm card space-y-4">
          <h2 className="text-xl font-bold text-gray-800 text-center">输入恢复口令</h2>
          <p className="text-sm text-gray-500 text-center">账号：{username}</p>

          <form onSubmit={handleDecryptRecovery} className="space-y-4">
            <div>
              <label htmlFor="recovery-ph" className="block text-sm font-medium text-gray-600 mb-1">恢复口令</label>
              <input
                id="recovery-ph"
                type="password"
                className="input-field"
                value={recoveryPhrase}
                onChange={(e) => setRecoveryPhrase(e.target.value)}
                placeholder="请输入你设置的恢复口令"
                autoComplete="off"
                disabled={loading}
              />
            </div>

            {error && <p className="text-red-500 text-sm text-center" role="alert">{error}</p>}

            <button type="submit" className="btn-primary w-full" disabled={loading || !recoveryPhrase}>
              {loading ? '验证中...' : '验证恢复口令'}
            </button>

            <button type="button" onClick={() => setStep('username')} className="btn-ghost w-full text-sm">
              返回上一步
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
      <div className="w-full max-w-sm card space-y-4">
        <h2 className="text-xl font-bold text-gray-800 text-center">找回密码</h2>
        <p className="text-sm text-gray-500 text-center">输入你的用户名以查找恢复信息</p>

        <form onSubmit={handleFetchRecovery} className="space-y-4">
          <div>
            <label htmlFor="rec-username" className="block text-sm font-medium text-gray-600 mb-1">用户名</label>
            <input
              id="rec-username"
              type="text"
              className="input-field"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="请输入用户名"
              autoComplete="username"
              disabled={loading}
            />
          </div>

          {error && <p className="text-red-500 text-sm text-center" role="alert">{error}</p>}

          <button type="submit" className="btn-primary w-full" disabled={loading || !username.trim()}>
            {loading ? '查找中...' : '查找账号'}
          </button>

          <div className="text-center text-sm">
            <Link to="/login" className="text-gray-400 hover:text-gray-500">返回登录</Link>
          </div>
        </form>
      </div>
    </div>
  );
}

export { parseEncryptedWithIv };
