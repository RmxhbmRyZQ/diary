import { WEATHER_OPTIONS } from '../../utils/constants';

interface WeatherPickerProps {
  value: string | null;
  onChange: (weather: string | null) => void;
}

export default function WeatherPicker({ value, onChange }: WeatherPickerProps) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {WEATHER_OPTIONS.map((w) => (
        <button
          key={w.value}
          type="button"
          onClick={() => onChange(value === w.value ? null : w.value)}
          className={`inline-flex items-center gap-1 px-2.5 py-1.5 rounded-full text-sm transition-all
            ${value === w.value
              ? 'bg-sage-200 text-sage-800 ring-1 ring-sage-400'
              : 'bg-gray-50 text-gray-500 hover:bg-sage-100 hover:text-sage-700'
            }`}
          title={w.label}
        >
          <span className="text-base">{w.emoji}</span>
          <span>{w.label}</span>
        </button>
      ))}
    </div>
  );
}
