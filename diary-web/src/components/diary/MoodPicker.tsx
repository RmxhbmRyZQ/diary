import { MOOD_OPTIONS } from '../../utils/constants';

interface MoodPickerProps {
  value: string | null;
  onChange: (mood: string | null) => void;
}

export default function MoodPicker({ value, onChange }: MoodPickerProps) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {MOOD_OPTIONS.map((mood) => (
        <button
          key={mood.value}
          type="button"
          onClick={() => onChange(value === mood.value ? null : mood.value)}
          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-full text-sm transition-all
            ${value === mood.value
              ? 'bg-warm-200 text-warm-800 ring-1 ring-warm-400'
              : 'bg-gray-50 text-gray-500 hover:bg-warm-100 hover:text-warm-700'
            }`}
          title={mood.label}
        >
          <span className="text-base">{mood.emoji}</span>
          <span>{mood.label}</span>
        </button>
      ))}
    </div>
  );
}
