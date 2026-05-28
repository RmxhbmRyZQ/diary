import { useState, useEffect } from 'react';
import { getUsers, deleteUser, type AdminUser } from '../../api/admin';
import { Trash2, AlertTriangle } from 'lucide-react';

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<AdminUser | null>(null);

  const fetchUsers = async () => {
    try {
      const result = await getUsers();
      setUsers(result.data);
    } catch {
      setError('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  async function handleDelete() {
    if (!deleteTarget) return;
    try {
      await deleteUser(deleteTarget.id);
      setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
      setDeleteTarget(null);
    } catch {
      setError('删除用户失败');
    }
  }

  if (loading) {
    return <p className="text-slate-400">加载中...</p>;
  }

  return (
    <div>
      <h2 className="text-xl font-bold text-slate-800 mb-6">用户管理</h2>

      {error && <p className="text-red-500 text-sm mb-4">{error}</p>}

      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-100">
              <th className="text-left px-5 py-3 text-slate-500 font-medium">用户名</th>
              <th className="text-left px-5 py-3 text-slate-500 font-medium">注册时间</th>
              <th className="text-left px-5 py-3 text-slate-500 font-medium">日记数</th>
              <th className="text-right px-5 py-3 text-slate-500 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 && (
              <tr>
                <td colSpan={4} className="text-center py-8 text-slate-300">暂无用户</td>
              </tr>
            )}
            {users.map((user) => (
              <tr key={user.id} className="border-b border-slate-50 hover:bg-slate-50 transition-colors">
                <td className="px-5 py-3 font-medium text-slate-800">{user.username}</td>
                <td className="px-5 py-3 text-slate-500">
                  {new Date(user.created_at).toLocaleDateString('zh-CN')}
                </td>
                <td className="px-5 py-3 text-slate-500">{user.entry_count}</td>
                <td className="px-5 py-3 text-right">
                  <button
                    onClick={() => setDeleteTarget(user)}
                    className="text-red-400 hover:text-red-600 transition-colors p-1"
                    title="删除用户"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Delete confirmation modal */}
      {deleteTarget && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <div className="flex items-center gap-3 mb-4 text-amber-600">
              <AlertTriangle className="w-6 h-6" />
              <h3 className="text-lg font-bold">确认删除用户</h3>
            </div>
            <p className="text-sm text-slate-500 mb-4">
              此操作不可逆，将删除用户 <strong>{deleteTarget.username}</strong> 的所有日记和附件。确定继续？
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setDeleteTarget(null)}
                className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-sm bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors"
              >
                确认删除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
