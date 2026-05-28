import { useState, type FormEvent } from 'react';
import { updateKdfConfig, updateRateLimit, updateAttachmentConfig } from '../../api/admin';
import { Save } from 'lucide-react';

type ConfigTab = 'kdf' | 'rate-limit' | 'attachments';

export default function AdminConfigPage() {
  const [tab, setTab] = useState<ConfigTab>('kdf');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // KDF
  const [algorithm, setAlgorithm] = useState('pbkdf2-sha256');
  const [iterations, setIterations] = useState('600000');

  // Rate limit
  const [endpoint, setEndpoint] = useState('login');
  const [limit, setLimit] = useState('5');

  // Attachments
  const [maxFileSizeMb, setMaxFileSizeMb] = useState('10');
  const [maxPerEntry, setMaxPerEntry] = useState('20');

  const endpointOptions = [
    { value: 'login', label: '登录' },
    { value: 'register', label: '注册' },
    { value: 'recovery', label: '恢复' },
    { value: 'api', label: '通用 API' },
    { value: 'attachment', label: '附件上传' },
  ];

  const tabs = [
    { key: 'kdf' as const, label: 'KDF 配置' },
    { key: 'rate-limit' as const, label: '限流配置' },
    { key: 'attachments' as const, label: '附件限制' },
  ];

  async function handleSaveKdf(e: FormEvent) {
    e.preventDefault();
    setError('');
    setMessage('');
    const iter = parseInt(iterations, 10);
    if (!algorithm.trim()) { setError('请选择算法'); return; }
    if (isNaN(iter) || iter < 100000) { setError('迭代次数最低 100000'); return; }

    setLoading(true);
    try {
      await updateKdfConfig(algorithm, iter);
      setMessage('KDF 配置已更新');
    } catch {
      setError('更新 KDF 配置失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveRateLimit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setMessage('');
    const lim = parseInt(limit, 10);
    if (!endpoint) { setError('请选择端点'); return; }
    if (isNaN(lim) || lim < 1) { setError('限流阈值最低 1'); return; }

    setLoading(true);
    try {
      await updateRateLimit(endpoint, lim);
      setMessage('限流配置已更新');
    } catch {
      setError('更新限流配置失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveAttachments(e: FormEvent) {
    e.preventDefault();
    setError('');
    setMessage('');
    const size = parseInt(maxFileSizeMb, 10);
    const per = parseInt(maxPerEntry, 10);
    if (isNaN(size) || size < 1 || size > 100) { setError('文件大小范围 1-100 MB'); return; }
    if (isNaN(per) || per < 1 || per > 50) { setError('附件数量范围 1-50'); return; }

    setLoading(true);
    try {
      await updateAttachmentConfig(size, per);
      setMessage('附件限制已更新');
    } catch {
      setError('更新附件限制失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h2 className="text-xl font-bold text-slate-800 mb-6">系统配置</h2>

      {message && <p className="text-green-600 text-sm mb-4">{message}</p>}
      {error && <p className="text-red-500 text-sm mb-4">{error}</p>}

      <div className="flex gap-1 mb-4 bg-slate-100 rounded-lg p-1 w-fit">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-3 py-1.5 rounded-md text-sm transition-colors ${
              tab === t.key ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl shadow-sm p-5">
        {tab === 'kdf' && (
          <form onSubmit={handleSaveKdf} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">算法</label>
              <select
                value={algorithm}
                onChange={(e) => setAlgorithm(e.target.value)}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              >
                <option value="pbkdf2-sha256">PBKDF2-SHA256</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">迭代次数（最低 100000）</label>
              <input
                type="number"
                value={iterations}
                onChange={(e) => setIterations(e.target.value)}
                min="100000"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              />
            </div>
            <button type="submit" className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm" disabled={loading}>
              <Save className="w-4 h-4" />
              保存配置
            </button>
          </form>
        )}

        {tab === 'rate-limit' && (
          <form onSubmit={handleSaveRateLimit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">端点</label>
              <select
                value={endpoint}
                onChange={(e) => setEndpoint(e.target.value)}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              >
                {endpointOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label} ({opt.value})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">限流次数（最低 1）</label>
              <input
                type="number"
                value={limit}
                onChange={(e) => setLimit(e.target.value)}
                min="1"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              />
            </div>
            <button type="submit" className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm" disabled={loading}>
              <Save className="w-4 h-4" />
              保存配置
            </button>
          </form>
        )}

        {tab === 'attachments' && (
          <form onSubmit={handleSaveAttachments} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">单文件最大大小（MB，1-100）</label>
              <input
                type="number"
                value={maxFileSizeMb}
                onChange={(e) => setMaxFileSizeMb(e.target.value)}
                min="1"
                max="100"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-600 mb-1">单篇日记最大附件数（1-50）</label>
              <input
                type="number"
                value={maxPerEntry}
                onChange={(e) => setMaxPerEntry(e.target.value)}
                min="1"
                max="50"
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                disabled={loading}
              />
            </div>
            <button type="submit" className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors text-sm" disabled={loading}>
              <Save className="w-4 h-4" />
              保存配置
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
