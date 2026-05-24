import { cn } from '@/lib/cn';

interface SliderProps {
  value: number;
  onChange: (value: number) => void;
  min: number;
  max: number;
  step: number;
  className?: string;
}

export function Slider({ value, onChange, min, max, step, className }: SliderProps) {
  const percent = ((value - min) / (max - min)) * 100;
  return (
    <div className={cn('relative h-8 flex items-center', className)}>
      <div className="absolute inset-x-0 h-1.5 bg-bg-elevated rounded-full" />
      <div
        className="absolute h-1.5 bg-accent rounded-full"
        style={{ width: `${percent}%` }}
      />
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="absolute inset-0 w-full opacity-0 cursor-pointer"
        style={{ height: '100%' }}
      />
      <div
        className="absolute size-4 rounded-full bg-accent border-2 border-white shadow-md pointer-events-none transition-transform"
        style={{ left: `calc(${percent}% - 8px)` }}
      />
    </div>
  );
}
