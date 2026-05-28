import { useEffect, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { getAdminToken } from '../../api/adminClient';
import { adminLogout } from '../../api/admin';
import { LayoutDashboard, Users, Settings, LogOut } from 'lucide-react';

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!getAdminToken()) {
      navigate('/admin/login', { replace: true });
    }
    setChecked(true);
  }, [navigate]);

  useEffect(() => {
    const handler = () => navigate('/admin/login', { replace: true });
    window.addEventListener('admin:session-expired', handler);
    return () => window.removeEventListener('admin:session-expired', handler);
  }, [navigate]);

  if (!checked || !getAdminToken()) return null;

  const isActive = (path: string) => location.pathname === path;

  const navItems = [
    { path: '/admin', icon: <LayoutDashboard className="w-4 h-4" />, label: '仪表盘' },
    { path: '/admin/users', icon: <Users className="w-4 h-4" />, label: '用户管理' },
    { path: '/admin/config', icon: <Settings className="w-4 h-4" />, label: '系统配置' },
  ];

  async function handleLogout() {
    try {
      await adminLogout();
    } catch {
      // proceed with local logout
    }
    navigate('/admin/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-100 flex">
      {/* Sidebar */}
      <aside className="w-56 bg-slate-800 text-white flex flex-col">
        <div className="px-5 py-4 border-b border-slate-700">
          <h1 className="text-lg font-bold">隐秘日记</h1>
          <p className="text-xs text-slate-400">管理后台</p>
        </div>
        <nav className="flex-1 px-2 py-3 space-y-1">
          {navItems.map((item) => (
            <button
              key={item.path}
              onClick={() => navigate(item.path)}
              className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors
                ${isActive(item.path) ? 'bg-indigo-600 text-white' : 'text-slate-300 hover:bg-slate-700 hover:text-white'}`}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>
        <div className="px-2 py-3 border-t border-slate-700">
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-slate-300 hover:bg-slate-700 hover:text-white transition-colors"
          >
            <LogOut className="w-4 h-4" />
            退出登录
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
