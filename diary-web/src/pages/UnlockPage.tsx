import { useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';

export default function UnlockPage() {
  const { unlock, user, logout } = useAuth();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (!password) {
      setError('请输入密码');
      return;
    }
    setLoading(true);
    try {
      await unlock(password);
    } catch (err: unknown) {
      setError((err as Error).message || '解锁失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-warm-50 px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-gray-800">隐秘日记</h1>
          <p className="text-gray-400 mt-1 text-sm">欢迎回来，{user?.username}</p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-4">
          <p className="text-sm text-gray-500 text-center">
            页面已刷新，请输入密码以解锁你的日记
          </p>

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
              autoFocus
              disabled={loading}
            />
          </div>

          {error && (
            <p className="text-red-500 text-sm text-center" role="alert">{error}</p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? '解锁中...' : '解锁'}
          </button>

          <button
            type="button"
            onClick={() => { logout().catch(() => {}); }}
            className="btn-ghost w-full text-sm"
          >
            切换账号
          </button>
        </form>
      </div>
    </div>
  );
}
