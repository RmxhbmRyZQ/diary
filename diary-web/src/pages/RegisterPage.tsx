import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import PasswordStrength from '../components/ui/PasswordStrength';

type Step = 'form' | 'recovery-key' | 'recovery-setup' | 'complete';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState<Step>('form');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [recoveryKey, setRecoveryKey] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [enableRecovery, setEnableRecovery] = useState(false);
  const [recoveryPassword, setRecoveryPassword] = useState('');

  function validatePassword(pw: string): string | null {
    if (pw.length < 8) return '密码至少 8 位';
    if (!/[A-Z]/.test(pw)) return '密码需包含大写字母';
    if (!/[a-z]/.test(pw)) return '密码需包含小写字母';
    if (!/[0-9]/.test(pw)) return '密码需包含数字';
    return null;
  }

  async function handleRegister(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (!username.trim()) {
      setError('请输入用户名');
      return;
    }
    if (username.trim().length < 3) {
      setError('用户名至少 3 个字符');
      return;
    }

    const pwError = validatePassword(password);
    if (pwError) {
      setError(pwError);
      return;
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      const result = await register(username.trim(), password);
      setRecoveryKey(result.recoveryKey);
      setStep('recovery-key');
    } catch (err: unknown) {
      const errorObj = err as Error;
      if ((err as { code?: number }).code === 409) {
        setError('用户名已存在');
      } else {
        setError(errorObj.message || '注册失败，请重试');
      }
    } finally {
      setLoading(false);
    }
  }

  function handleRecoveryConfirm() {
    if (!confirmed) return;
    if (enableRecovery) {
      setStep('recovery-setup');
    } else {
      setStep('complete');
    }
  }

  if (step === 'complete') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
        <div className="w-full max-w-sm text-center card">
          <h2 className="text-xl font-bold text-gray-800 mb-2">注册完成</h2>
          <p className="text-gray-500 mb-4">请使用刚才设置的用户名和密码登录</p>
          <Link to="/login" className="btn-primary inline-block">前往登录</Link>
        </div>
      </div>
    );
  }

  if (step === 'recovery-key') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
        <div className="w-full max-w-sm card space-y-4">
          <h2 className="text-xl font-bold text-gray-800 text-center">保存你的恢复密钥</h2>
          <p className="text-sm text-gray-500 text-center">
            如果你忘记了密码，恢复密钥是找回数据的<strong>唯一途径</strong>。请务必安全保存。
          </p>

          <div className="bg-warm-100 rounded-xl p-4">
            <p className="text-sm font-mono text-gray-700 break-all select-all leading-relaxed">
              {recoveryKey}
            </p>
          </div>

          <button
            onClick={() => {
              navigator.clipboard.writeText(recoveryKey).catch(() => {});
            }}
            className="btn-ghost w-full text-sm"
          >
            复制到剪贴板
          </button>

          <label className="flex items-start gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              className="mt-1"
            />
            <span className="text-sm text-gray-600">
              我已将恢复密钥安全保存在离线位置（如抄写在纸上或保存到加密文件中），我知道丢失恢复密钥和密码将导致数据永久无法恢复。
            </span>
          </label>

          <label className="flex items-start gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={enableRecovery}
              onChange={(e) => setEnableRecovery(e.target.checked)}
              className="mt-1"
            />
            <span className="text-sm text-gray-600">
              设置恢复口令托管（可选，可在设置中随时开启或关闭）
            </span>
          </label>

          <button
            onClick={handleRecoveryConfirm}
            disabled={!confirmed}
            className="btn-primary w-full"
          >
            继续
          </button>
        </div>
      </div>
    );
  }

  if (step === 'recovery-setup') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
        <div className="w-full max-w-sm card space-y-4">
          <h2 className="text-xl font-bold text-gray-800 text-center">设置恢复口令</h2>
          <p className="text-sm text-gray-500 text-center">
            设置一个与登录密码不同的恢复口令，用于忘记密码时恢复数据。
          </p>

          <div>
            <label htmlFor="recovery-pw" className="block text-sm font-medium text-gray-600 mb-1">
              恢复口令
            </label>
            <input
              id="recovery-pw"
              type="password"
              className="input-field"
              value={recoveryPassword}
              onChange={(e) => setRecoveryPassword(e.target.value)}
              placeholder="请输入恢复口令（不同于登录密码）"
            />
            <PasswordStrength password={recoveryPassword} />
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => setStep('complete')}
              className="btn-ghost flex-1 text-sm"
            >
              跳过
            </button>
            <button
              onClick={() => setStep('complete')}
              disabled={!recoveryPassword || recoveryPassword.length < 8}
              className="btn-primary flex-1"
            >
              保存并继续
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-800">创建账号</h1>
          <p className="text-gray-400 mt-1 text-sm">开始你的私密记录之旅</p>
        </div>

        <form onSubmit={handleRegister} className="card space-y-4">
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-600 mb-1">
              用户名
            </label>
            <input
              id="username"
              type="text"
              className="input-field"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="3-32 字符，字母数字下划线"
              autoComplete="username"
              disabled={loading}
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-600 mb-1">
              密码
            </label>
            <input
              id="password"
              type="password"
              className="input-field"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少 8 位，含大小写字母和数字"
              autoComplete="new-password"
              disabled={loading}
            />
            <PasswordStrength password={password} />
          </div>

          <div>
            <label htmlFor="confirm-password" className="block text-sm font-medium text-gray-600 mb-1">
              确认密码
            </label>
            <input
              id="confirm-password"
              type="password"
              className="input-field"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="请再次输入密码"
              autoComplete="new-password"
              disabled={loading}
            />
          </div>

          {error && (
            <p className="text-red-500 text-sm text-center" role="alert">{error}</p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? '注册中...' : '注册'}
          </button>

          <div className="text-center text-sm">
            <Link to="/login" className="text-warm-600 hover:text-warm-700">
              已有账号？去登录
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
