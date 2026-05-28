interface PasswordStrengthProps {
  password: string;
}

interface StrengthResult {
  level: 'weak' | 'medium' | 'strong';
  label: string;
  color: string;
  width: string;
}

function evaluateStrength(password: string): StrengthResult {
  if (!password) {
    return { level: 'weak', label: '', color: 'bg-gray-200', width: '0%' };
  }

  const hasMinLength = password.length >= 8;
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);

  const categories = [hasUpper, hasLower, hasDigit].filter(Boolean).length;

  if (!hasMinLength || categories < 2) {
    return {
      level: 'weak',
      label: hasMinLength ? '弱 — 需包含大小写字母和数字' : '弱 — 至少 8 位',
      color: 'bg-red-400',
      width: '33%',
    };
  }

  if (categories === 3) {
    return { level: 'strong', label: '强', color: 'bg-green-400', width: '100%' };
  }

  return { level: 'medium', label: '中 — 建议添加数字', color: 'bg-yellow-400', width: '66%' };
}

export default function PasswordStrength({ password }: PasswordStrengthProps) {
  const { level, label, color, width } = evaluateStrength(password);

  if (!password) return null;

  return (
    <div className="mt-2 space-y-1">
      <div className="flex gap-1">
        <div className={`h-1.5 rounded-full flex-1 transition-colors ${level === 'weak' ? 'bg-red-400' : 'bg-green-400'}`} />
        <div className={`h-1.5 rounded-full flex-1 transition-colors ${level === 'strong' ? 'bg-green-400' : level === 'medium' ? 'bg-yellow-400' : 'bg-gray-200'}`} />
        <div className={`h-1.5 rounded-full flex-1 transition-colors ${level === 'strong' ? 'bg-green-400' : 'bg-gray-200'}`} />
      </div>
      <p className={`text-xs ${level === 'weak' ? 'text-red-500' : level === 'medium' ? 'text-yellow-600' : 'text-green-600'}`}>
        {label}
      </p>
    </div>
  );
}

export { evaluateStrength };
export type { StrengthResult };
