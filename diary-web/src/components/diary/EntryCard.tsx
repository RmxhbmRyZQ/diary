import { getMoodEmoji, getWeatherEmoji } from '../../utils/constants';
import type { CachedEntry } from '../../db/entries';
import { Star } from 'lucide-react';

interface EntryCardProps {
  entry: CachedEntry;
  onClick: () => void;
}

export default function EntryCard({ entry, onClick }: EntryCardProps) {
  const dateObj = new Date(entry.diaryDate + 'T00:00:00');
  const monthDay = `${dateObj.getMonth() + 1}月${dateObj.getDate()}日`;

  return (
    <div
      onClick={onClick}
      className="card p-4 cursor-pointer hover:shadow-md transition-shadow group"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs text-gray-400">{monthDay}</span>
            {entry.mood && <span className="text-sm">{getMoodEmoji(entry.mood)}</span>}
            {entry.weather && <span className="text-sm">{getWeatherEmoji(entry.weather)}</span>}
            {entry.favorite && (
              <Star className="w-3.5 h-3.5 fill-yellow-400 text-yellow-400" />
            )}
          </div>
          <h3 className="font-medium text-gray-800 truncate group-hover:text-warm-700 transition-colors">
            {entry.title || '无标题'}
          </h3>
          {entry.summary && (
            <p className="text-sm text-gray-400 mt-1 line-clamp-2">{entry.summary}</p>
          )}
        </div>
      </div>
      {entry.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {entry.tags.map((tag) => (
            <span key={tag} className="text-xs px-1.5 py-0.5 rounded bg-warm-100 text-warm-600">
              {tag}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
