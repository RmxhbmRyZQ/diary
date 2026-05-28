export const MOOD_OPTIONS = [
  { value: 'happy', label: '开心', emoji: '😊' },
  { value: 'excited', label: '兴奋', emoji: '🤩' },
  { value: 'calm', label: '平静', emoji: '😌' },
  { value: 'sad', label: '难过', emoji: '😢' },
  { value: 'angry', label: '生气', emoji: '😡' },
  { value: 'anxious', label: '焦虑', emoji: '😰' },
  { value: 'grateful', label: '感恩', emoji: '🙏' },
  { value: 'loved', label: '幸福', emoji: '🥰' },
] as const;

export const WEATHER_OPTIONS = [
  { value: 'sunny', label: '晴', emoji: '☀️' },
  { value: 'cloudy', label: '多云', emoji: '⛅' },
  { value: 'rainy', label: '雨', emoji: '🌧️' },
  { value: 'snowy', label: '雪', emoji: '❄️' },
  { value: 'windy', label: '风', emoji: '💨' },
  { value: 'foggy', label: '雾', emoji: '🌫️' },
  { value: 'stormy', label: '暴风雨', emoji: '⛈️' },
] as const;

export type MoodValue = (typeof MOOD_OPTIONS)[number]['value'];
export type WeatherValue = (typeof WEATHER_OPTIONS)[number]['value'];

export function getMoodLabel(value: string): string {
  return MOOD_OPTIONS.find((m) => m.value === value)?.label || value;
}

export function getMoodEmoji(value: string): string {
  return MOOD_OPTIONS.find((m) => m.value === value)?.emoji || '❓';
}

export function getWeatherLabel(value: string): string {
  return WEATHER_OPTIONS.find((w) => w.value === value)?.label || value;
}

export function getWeatherEmoji(value: string): string {
  return WEATHER_OPTIONS.find((w) => w.value === value)?.emoji || '❓';
}
