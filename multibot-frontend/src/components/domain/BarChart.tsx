import { cn } from '@/lib/cn';

export interface BarChartPoint {
  label: string;
  value: number;
  tooltip?: string;
  dim?: boolean;
}

interface BarChartProps {
  data: BarChartPoint[];
  color?: 'accent' | 'info';
  heightPx?: number;
}

export function BarChart({ data, color = 'accent', heightPx = 180 }: BarChartProps) {
  const max = Math.max(...data.map((d) => d.value), 1);

  const colorClass = color === 'info' ? 'bg-info' : 'bg-accent';
  const dimClass = color === 'info' ? 'bg-info/40' : 'bg-accent-dim';

  return (
    <div>
      <div className="flex items-end gap-1" style={{ height: `${heightPx}px`, paddingTop: '8px' }}>
        {data.map((point, i) => {
          const heightPercent = (point.value / max) * 100;
          return (
            <div
              key={i}
              className={cn(
                'flex-1 rounded-t relative group cursor-default transition-opacity hover:opacity-80',
                point.dim ? dimClass : colorClass
              )}
              style={{ height: `${Math.max(heightPercent, 2)}%`, minHeight: '4px' }}
            >
              <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 bg-bg-hover border border-border-strong px-2 py-1 rounded text-[11px] whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                {point.tooltip ?? `${point.label}: ${point.value}`}
              </div>
            </div>
          );
        })}
      </div>
      <div className="flex gap-1 mt-1.5 text-[10px] text-text-dim">
        {data.map((point, i) => (
          <span key={i} className="flex-1 text-center">{point.label}</span>
        ))}
      </div>
    </div>
  );
}
