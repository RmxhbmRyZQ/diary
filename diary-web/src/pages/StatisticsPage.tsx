import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAllEntries, type CachedEntry } from '../db/entries';
import { getMoodLabel } from '../utils/constants';
import { ArrowLeft } from 'lucide-react';

interface HeatmapDay {
  date: string;
  count: number;
}

export default function StatisticsPage() {
  const navigate = useNavigate();
  const [entries, setEntries] = useState<CachedEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const all = await getAllEntries();
      setEntries(all);
      setLoading(false);
    })();
  }, []);

  const stats = useMemo(() => {
    const totalEntries = entries.length;
    const totalWords = entries.reduce((sum, e) => sum + e.content.length, 0);

    const daySet = new Set(entries.map((e) => e.diaryDate));
    const totalDays = daySet.size;

    // Consecutive days
    let maxStreak = 0;
    let currentStreak = 0;
    const today = new Date().toISOString().slice(0, 10);
    const sortedDates = Array.from(daySet).sort();
    for (let i = 0; i < sortedDates.length; i++) {
      if (i === 0) {
        currentStreak = 1;
      } else {
        const prev = new Date(sortedDates[i - 1]);
        const curr = new Date(sortedDates[i]);
        const diff = (curr.getTime() - prev.getTime()) / (1000 * 60 * 60 * 24);
        if (diff === 1) {
          currentStreak++;
        } else {
          currentStreak = 1;
        }
      }
      maxStreak = Math.max(maxStreak, currentStreak);
    }

    // Mood distribution
    const moodCounts = new Map<string, number>();
    for (const e of entries) {
      if (e.mood) {
        moodCounts.set(e.mood, (moodCounts.get(e.mood) || 0) + 1);
      }
    }
    const moodDistribution = Array.from(moodCounts.entries())
      .map(([mood, count]) => ({ mood: getMoodLabel(mood), count, percentage: (count / totalEntries) * 100 }))
      .sort((a, b) => b.count - a.count);

    // Tag cloud
    const tagCounts = new Map<string, number>();
    for (const e of entries) {
      for (const tag of e.tags) {
        tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
      }
    }
    const tagCloud = Array.from(tagCounts.entries())
      .map(([tag, count]) => ({ tag, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 30);

    const maxTagCount = tagCloud.length > 0 ? tagCloud[0].count : 1;

    // Monthly words
    const monthlyWords = new Map<string, number>();
    for (const e of entries) {
      const ym = e.diaryDate.substring(0, 7);
      monthlyWords.set(ym, (monthlyWords.get(ym) || 0) + e.content.length);
    }
    const monthlyTrend = Array.from(monthlyWords.entries())
      .sort((a, b) => a[0].localeCompare(b[0]));
    const maxMonthlyWords = Math.max(1, ...monthlyTrend.map(([, c]) => c));

    // Heatmap (last 365 days)
    const heatmap: HeatmapDay[] = [];
    const countByDate = new Map<string, number>();
    for (const e of entries) {
      countByDate.set(e.diaryDate, (countByDate.get(e.diaryDate) || 0) + 1);
    }
    const maxDayCount = Math.max(1, ...Array.from(countByDate.values()));

    for (let i = 365; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      const dateStr = d.toISOString().slice(0, 10);
      heatmap.push({ date: dateStr, count: countByDate.get(dateStr) || 0 });
    }

    return {
      totalEntries, totalWords, totalDays, maxStreak,
      moodDistribution, tagCloud, maxTagCount,
      monthlyTrend, maxMonthlyWords,
      heatmap, maxDayCount,
    };
  }, [entries]);

  function getHeatColor(count: number, max: number): string {
    if (count === 0) return 'bg-gray-100';
    const intensity = count / max;
    if (intensity < 0.25) return 'bg-warm-200';
    if (intensity < 0.5) return 'bg-warm-300';
    if (intensity < 0.75) return 'bg-warm-400';
    return 'bg-warm-500';
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-warm-50">
        <p className="text-gray-400">加载中...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-warm-50">
      <div className="max-w-2xl mx-auto px-4 py-6">
        <div className="flex items-center gap-4 mb-6">
          <button onClick={() => navigate(-1)} className="p-1.5 hover:bg-warm-100 rounded-lg transition-colors">
            <ArrowLeft className="w-5 h-5 text-gray-500" />
          </button>
          <h1 className="text-xl font-bold text-gray-800">统计</h1>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 gap-3 mb-6">
          <div className="card p-4 text-center">
            <p className="text-2xl font-bold text-warm-600">{stats.totalEntries}</p>
            <p className="text-xs text-gray-400 mt-1">总日记数</p>
          </div>
          <div className="card p-4 text-center">
            <p className="text-2xl font-bold text-warm-600">{stats.totalDays}</p>
            <p className="text-xs text-gray-400 mt-1">写作天数</p>
          </div>
          <div className="card p-4 text-center">
            <p className="text-2xl font-bold text-warm-600">{stats.maxStreak}</p>
            <p className="text-xs text-gray-400 mt-1">最长连续天数</p>
          </div>
          <div className="card p-4 text-center">
            <p className="text-2xl font-bold text-warm-600">{stats.totalWords.toLocaleString()}</p>
            <p className="text-xs text-gray-400 mt-1">总字数</p>
          </div>
        </div>

        {/* Mood distribution */}
        <div className="card p-4 mb-6">
          <h2 className="text-sm font-medium text-gray-600 mb-3">心情分布</h2>
          {stats.moodDistribution.length === 0 ? (
            <p className="text-sm text-gray-300">暂无数据</p>
          ) : (
            <div className="space-y-2">
              {stats.moodDistribution.map((item) => (
                <div key={item.mood} className="flex items-center gap-2">
                  <span className="text-sm text-gray-600 w-12">{item.mood}</span>
                  <div className="flex-1 h-4 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-warm-400 rounded-full transition-all"
                      style={{ width: `${item.percentage}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-400 w-10 text-right">{item.count}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Monthly trend */}
        <div className="card p-4 mb-6">
          <h2 className="text-sm font-medium text-gray-600 mb-3">月度字数趋势</h2>
          {stats.monthlyTrend.length === 0 ? (
            <p className="text-sm text-gray-300">暂无数据</p>
          ) : (
            <div className="flex items-end gap-1 h-24">
              {stats.monthlyTrend.map(([ym, count]) => (
                <div key={ym} className="flex-1 flex flex-col items-center gap-1">
                  <span className="text-xs text-gray-400">{count}</span>
                  <div
                    className="w-full bg-warm-400 rounded-t transition-all"
                    style={{ height: `${Math.max(4, (count / stats.maxMonthlyWords) * 80)}%` }}
                  />
                  <span className="text-xs text-gray-300">{ym.substring(5)}月</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Tag cloud */}
        <div className="card p-4 mb-6">
          <h2 className="text-sm font-medium text-gray-600 mb-3">标签云</h2>
          {stats.tagCloud.length === 0 ? (
            <p className="text-sm text-gray-300">暂无标签</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {stats.tagCloud.map(({ tag, count }) => {
                const size = 0.75 + (count / stats.maxTagCount) * 0.75;
                const opacity = 0.5 + (count / stats.maxTagCount) * 0.5;
                return (
                  <span
                    key={tag}
                    className="inline-block px-2 py-1 rounded-full bg-warm-100 text-warm-700"
                    style={{ fontSize: `${size}rem`, opacity }}
                  >
                    {tag}
                    <span className="text-warm-400 ml-1 text-xs">({count})</span>
                  </span>
                );
              })}
            </div>
          )}
        </div>

        {/* Heatmap */}
        <div className="card p-4">
          <h2 className="text-sm font-medium text-gray-600 mb-3">写作热力图</h2>
          <div className="flex flex-wrap gap-1">
            {stats.heatmap.map((day) => (
              <div
                key={day.date}
                className={`w-3 h-3 rounded-sm ${getHeatColor(day.count, stats.maxDayCount)}`}
                title={`${day.date}: ${day.count} 篇`}
              />
            ))}
          </div>
          <div className="flex items-center gap-1 mt-2 text-xs text-gray-300">
            <span>少</span>
            <div className="w-3 h-3 rounded-sm bg-gray-100" />
            <div className="w-3 h-3 rounded-sm bg-warm-200" />
            <div className="w-3 h-3 rounded-sm bg-warm-300" />
            <div className="w-3 h-3 rounded-sm bg-warm-400" />
            <div className="w-3 h-3 rounded-sm bg-warm-500" />
            <span>多</span>
          </div>
        </div>
      </div>
    </div>
  );
}
