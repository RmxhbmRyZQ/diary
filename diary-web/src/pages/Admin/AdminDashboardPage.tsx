import { useState, useEffect } from 'react';
import { getDashboard, type DashboardData } from '../../api/admin';
import { Users, FileText, HardDrive } from 'lucide-react';

function formatBytes(bytes: number): string {
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(2) + ' GB';
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(2) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(2) + ' KB';
  return bytes + ' B';
}

export default function AdminDashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const result = await getDashboard();
        setData(result.data);
      } catch {
        setError('加载仪表盘数据失败');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return <p className="text-slate-400">加载中...</p>;
  }

  if (error || !data) {
    return <p className="text-red-500">{error || '获取数据失败'}</p>;
  }

  const cards = [
    { label: '总用户数', value: data.total_users.toLocaleString(), icon: <Users className="w-5 h-5" />, color: 'bg-indigo-50 text-indigo-600' },
    { label: '总日记数', value: data.total_entries.toLocaleString(), icon: <FileText className="w-5 h-5" />, color: 'bg-emerald-50 text-emerald-600' },
    { label: '存储大小', value: formatBytes(data.storage_bytes), icon: <HardDrive className="w-5 h-5" />, color: 'bg-amber-50 text-amber-600' },
  ];

  return (
    <div>
      <h2 className="text-xl font-bold text-slate-800 mb-6">仪表盘</h2>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {cards.map((card) => (
          <div key={card.label} className="bg-white rounded-xl shadow-sm p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-slate-500">{card.label}</span>
              <span className={`p-2 rounded-lg ${card.color}`}>{card.icon}</span>
            </div>
            <p className="text-2xl font-bold text-slate-800">{card.value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
