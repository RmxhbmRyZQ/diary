import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getAllEntries, decryptCachedEntry, type CachedEntry } from '../db/entries';
import { fullSync } from '../services/syncService';
import EntryCard from '../components/diary/EntryCard';
import MoodPicker from '../components/diary/MoodPicker';
import WeatherPicker from '../components/diary/WeatherPicker';
import { MOOD_OPTIONS } from '../utils/constants';
import { Plus, Search, Star, Filter, LogOut, BarChart3, Settings } from 'lucide-react';

type FilterMode = 'all' | 'favorites';

export default function DiaryListPage() {
  const navigate = useNavigate();
  const { user, dek, logout, needsKdfUpgrade, dismissKdfUpgrade } = useAuth();

  const [entries, setEntries] = useState<CachedEntry[]>([]);
  const [filterMode, setFilterMode] = useState<FilterMode>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [moodFilter, setMoodFilter] = useState<string | null>(null);
  const [weatherFilter, setWeatherFilter] = useState<string | null>(null);
  const [selectedYear, setSelectedYear] = useState<string | null>(null);
  const [selectedMonth, setSelectedMonth] = useState<string | null>(null);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(true);
  const [syncError, setSyncError] = useState('');

  useEffect(() => {
    if (!dek || !user) {
      setSyncing(false);
      return;
    }
    (async () => {
      try {
        await fullSync(user.username);
      } catch (err) {
        const msg = err instanceof Error ? err.message : '同步失败，请检查网络连接';
        setSyncError(msg);
      }
      try {
        const encrypted = await getAllEntries();
        const results = await Promise.allSettled(encrypted.map((e) => decryptCachedEntry(e, dek)));
        const all = results
          .filter((r): r is PromiseFulfilledResult<CachedEntry> => r.status === 'fulfilled')
          .map((r) => r.value);
        all.sort((a, b) => b.diaryDate.localeCompare(a.diaryDate));
        setEntries(all);
      } finally {
        setSyncing(false);
      }
    })();
  }, [dek, user]);

  const filteredEntries = useMemo(() => {
    let result = entries;

    if (filterMode === 'favorites') {
      result = result.filter((e) => e.favorite);
    }

    if (moodFilter) {
      result = result.filter((e) => e.mood === moodFilter);
    }

    if (weatherFilter) {
      result = result.filter((e) => e.weather === weatherFilter);
    }

    if (selectedYear) {
      result = result.filter((e) => e.diaryDate.startsWith(selectedYear));
    }
    if (selectedMonth) {
      result = result.filter((e) => e.diaryDate.startsWith(selectedMonth));
    }
    if (selectedDay) {
      result = result.filter((e) => e.diaryDate === selectedDay);
    }

    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      result = result.filter(
        (e) =>
          e.title.toLowerCase().includes(q) ||
          e.content.toLowerCase().includes(q) ||
          e.tags.some((t) => t.toLowerCase().includes(q)),
      );
    }

    return result;
  }, [entries, filterMode, moodFilter, weatherFilter, selectedYear, selectedMonth, selectedDay, searchQuery]);

  const availableYears = useMemo(() => {
    const years = new Set(entries.map((e) => e.diaryDate.substring(0, 4)));
    return Array.from(years).sort((a, b) => b.localeCompare(a));
  }, [entries]);

  const availableMonths = useMemo(() => {
    if (!selectedYear) return [];
    const months = new Set(
      entries
        .filter((e) => e.diaryDate.startsWith(selectedYear))
        .map((e) => e.diaryDate.substring(0, 7)),
    );
    return Array.from(months).sort((a, b) => b.localeCompare(a));
  }, [entries, selectedYear]);

  const availableDays = useMemo(() => {
    if (!selectedMonth) return [];
    const days = new Set(
      entries
        .filter((e) => e.diaryDate.startsWith(selectedMonth))
        .map((e) => e.diaryDate),
    );
    return Array.from(days).sort((a, b) => b.localeCompare(a));
  }, [entries, selectedMonth]);

  const groupedEntries = useMemo(() => {
    const groups = new Map<string, CachedEntry[]>();
    const groupKey = selectedMonth ? 'day' : 'month';
    for (const entry of filteredEntries) {
      const key = groupKey === 'day'
        ? entry.diaryDate
        : entry.diaryDate.substring(0, 7);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(entry);
    }
    return groups;
  }, [filteredEntries, selectedMonth]);

  const formatGroupLabel = (key: string) => {
    if (selectedMonth) {
      const d = new Date(key + 'T00:00:00');
      const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
      return `${parseInt(key.substring(8, 10))}日 ${weekdays[d.getDay()]}`;
    }
    const [year, month] = key.split('-');
    return `${year}年${parseInt(month)}月`;
  };

  const formatMonthLabel = (ym: string) => {
    return `${parseInt(ym.substring(5, 7))}月`;
  };

  const formatDayLabel = (dateStr: string) => {
    const d = new Date(dateStr + 'T00:00:00');
    const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
    return `${parseInt(dateStr.substring(8, 10))} 周${weekdays[d.getDay()]}`;
  };

  const hasFilters = filterMode === 'favorites' || moodFilter || weatherFilter || searchQuery.trim()
    || selectedYear || selectedMonth || selectedDay;

  function clearTimeFilter() {
    setSelectedYear(null);
    setSelectedMonth(null);
    setSelectedDay(null);
  }

  return (
    <div className="min-h-screen bg-warm-50">
      <div className="max-w-2xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-gray-800">隐秘日记</h1>
            {!syncing && (
              <span className="text-xs text-gray-300 bg-gray-100 px-1.5 py-0.5 rounded">已加密</span>
            )}
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => navigate('/statistics')}
              className="p-2 hover:bg-warm-100 rounded-lg transition-colors"
              title="统计"
            >
              <BarChart3 className="w-4 h-4 text-gray-400" />
            </button>
            <button
              onClick={() => navigate('/settings')}
              className="p-2 hover:bg-warm-100 rounded-lg transition-colors"
              title="设置"
            >
              <Settings className="w-4 h-4 text-gray-400" />
            </button>
            <button
              onClick={() => { logout().catch(() => {}); }}
              className="p-2 hover:bg-warm-100 rounded-lg transition-colors"
              title="登出"
            >
              <LogOut className="w-4 h-4 text-gray-400" />
            </button>
          </div>
        </div>

        {/* KDF Upgrade Banner */}
        {needsKdfUpgrade && (
          <div className="mb-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-amber-500 text-sm">
                建议修改密码以升级加密强度，更好保护你的数据安全
              </span>
            </div>
            <div className="flex items-center gap-2 ml-2 shrink-0">
              <button
                onClick={() => navigate('/settings')}
                className="text-xs text-warm-600 hover:text-warm-700 font-medium whitespace-nowrap"
              >
                去修改
              </button>
              <button
                onClick={dismissKdfUpgrade}
                className="text-xs text-gray-400 hover:text-gray-500 whitespace-nowrap"
              >
                忽略
              </button>
            </div>
          </div>
        )}

        {/* Search & Filters */}
        <div className="space-y-3 mb-6">
          <div className="flex gap-2">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="搜索标题、内容或标签..."
                className="input-field pl-9 text-sm"
              />
            </div>
            <button
              onClick={() => setFilterMode(filterMode === 'all' ? 'favorites' : 'all')}
              className={`p-2 rounded-lg transition-colors ${
                filterMode === 'favorites' ? 'bg-yellow-100 text-yellow-600' : 'text-gray-300 hover:text-yellow-400'
              }`}
              title="收藏筛选"
            >
              <Star className={`w-5 h-5 ${filterMode === 'favorites' ? 'fill-yellow-400' : ''}`} />
            </button>
          </div>

          {/* Time Filter — 年 → 月 → 日 */}
          <div className="space-y-2">
            <div className="flex flex-wrap gap-1.5 items-center">
              <span className="text-xs text-gray-400 mr-1">时间</span>
              {availableYears.map((year) => (
                <button
                  key={year}
                  onClick={() => {
                    if (selectedYear === year) { clearTimeFilter(); return; }
                    setSelectedYear(year);
                    setSelectedMonth(null);
                    setSelectedDay(null);
                  }}
                  className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                    selectedYear === year
                      ? 'bg-warm-500 text-white font-medium'
                      : 'bg-white text-gray-700 hover:bg-warm-100'
                  }`}
                >
                  {year}
                </button>
              ))}
            </div>

            {selectedYear && availableMonths.length > 0 && (
              <div className="flex flex-wrap gap-1.5 items-center">
                <span className="text-xs text-gray-400 mr-1">月</span>
                {availableMonths.map((ym) => (
                  <button
                    key={ym}
                    onClick={() => {
                      if (selectedMonth === ym) { setSelectedMonth(null); setSelectedDay(null); return; }
                      setSelectedMonth(ym);
                      setSelectedDay(null);
                    }}
                    className={`px-3 py-1.5 text-sm rounded-lg transition-colors ${
                      selectedMonth === ym
                        ? 'bg-warm-400 text-white font-medium'
                        : 'bg-gray-50 text-gray-700 hover:bg-warm-100'
                    }`}
                  >
                    {formatMonthLabel(ym)}
                  </button>
                ))}
              </div>
            )}

            {selectedMonth && availableDays.length > 0 && (
              <div className="flex flex-wrap gap-1.5 items-center">
                <span className="text-xs text-gray-400 mr-1">日</span>
                {availableDays.map((day) => (
                  <button
                    key={day}
                    onClick={() => {
                      if (selectedDay === day) { setSelectedDay(null); return; }
                      setSelectedDay(day);
                    }}
                    className={`px-2.5 py-1.5 text-sm rounded-lg transition-colors ${
                      selectedDay === day
                        ? 'bg-warm-300 text-white font-medium'
                        : 'bg-gray-50 text-gray-600 hover:bg-warm-100'
                    }`}
                  >
                    {formatDayLabel(day)}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="flex flex-wrap gap-2">
            <MoodPicker value={moodFilter} onChange={setMoodFilter} />
            <WeatherPicker value={weatherFilter} onChange={setWeatherFilter} />
            {hasFilters && (
              <button
                onClick={() => {
                  setFilterMode('all');
                  setMoodFilter(null);
                  setWeatherFilter(null);
                  setSearchQuery('');
                  clearTimeFilter();
                }}
                className="text-xs text-warm-600 hover:text-warm-700 px-2 py-1"
              >
                清除筛选
              </button>
            )}
          </div>
        </div>

        {/* Sync status */}
        {syncing && (
          <p className="text-center text-gray-400 text-sm py-8">同步中...</p>
        )}
        {syncError && (
          <p className="text-center text-yellow-600 text-sm py-4">{syncError}</p>
        )}

        {/* Entry list */}
        {!syncing && filteredEntries.length === 0 && (
          <div className="text-center py-16">
            <p className="text-gray-400 mb-2">
              {hasFilters ? '没有匹配的日记' : '还没有日记'}
            </p>
            {!hasFilters && (
              <p className="text-gray-300 text-sm">点击右下角 + 开始写第一篇日记</p>
            )}
          </div>
        )}

        {Array.from(groupedEntries.entries()).map(([key, groupEntries]) => (
          <div key={key} className="mb-6">
            <h2 className="text-sm font-medium text-gray-400 mb-3 sticky top-0 bg-warm-50 py-1">
              {formatGroupLabel(key)}
              <span className="ml-2 text-xs text-gray-300">{groupEntries.length} 篇</span>
            </h2>
            <div className="space-y-3">
              {groupEntries.map((entry) => (
                <EntryCard
                  key={entry.diaryId}
                  entry={entry}
                  onClick={() => navigate(`/editor/${entry.diaryId}`)}
                />
              ))}
            </div>
          </div>
        ))}

        {/* FAB */}
        <button
          onClick={() => navigate('/editor/new')}
          className="fixed bottom-8 right-8 w-14 h-14 bg-warm-500 hover:bg-warm-600 text-white
                     rounded-full shadow-lg flex items-center justify-center transition-all
                     hover:shadow-xl hover:scale-105 active:scale-95"
        >
          <Plus className="w-6 h-6" />
        </button>
      </div>
    </div>
  );
}
