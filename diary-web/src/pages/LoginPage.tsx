import { useState, useEffect, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import PasswordStrength from '../components/ui/PasswordStrength';
import { recordLoginFailure, getLoginCooldownSeconds, resetLoginFailures } from '../utils/loginDelay';

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    getLoginCooldownSeconds().then((seconds) => {
      if (seconds > 0) setCooldown(seconds);
    });
  }, []);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => {
        const next = prev - 1;
        if (next <= 0) return 0;
        return next;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown > 0]);

  const isBlocked = cooldown > 0;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (!username.trim() || !password) {
      setError('请输入用户名和密码');
      return;
    }

    if (isBlocked) return;

    setLoading(true);
    try {
      await login(username.trim(), password);
      await resetLoginFailures();
    } catch (err: unknown) {
      const errorObj = err as Error;
      setError(errorObj.message || '登录失败，请重试');
      await recordLoginFailure();
      const sec = await getLoginCooldownSeconds();
      if (sec > 0) setCooldown(sec);
    } finally {
      setLoading(false);
    }
  }

  const formatCooldown = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-800">隐秘日记</h1>
          <p className="text-gray-400 mt-1 text-sm">你的秘密，只有你知道</p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-4">
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
              placeholder="请输入用户名"
              autoComplete="username"
              disabled={loading || isBlocked}
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
              placeholder="请输入密码"
              autoComplete="current-password"
              disabled={loading || isBlocked}
            />
            <PasswordStrength password={password} />
          </div>

          {error && (
            <p className="text-red-500 text-sm text-center" role="alert">{error}</p>
          )}

          {isBlocked && (
            <p className="text-yellow-600 text-sm text-center" role="status">
              登录尝试次数过多，请在 {formatCooldown(cooldown)} 后重试
            </p>
          )}

          <button
            type="submit"
            className="btn-primary w-full"
            disabled={loading || isBlocked}
          >
            {loading ? '登录中...' : '登录'}
          </button>

          <div className="flex justify-between text-sm">
            <Link to="/register" className="text-warm-600 hover:text-warm-700">
              注册账号
            </Link>
            <Link to="/recovery" className="text-gray-400 hover:text-gray-500">
              忘记密码？
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
