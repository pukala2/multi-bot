import type { components } from '@/api/schema';

type Cell = components['schemas']['AnalyticsHeatmapCell'];

interface HeatmapProps {
  cells: Cell[];
}

const DAY_LABELS = ['pn', 'wt', 'śr', 'cz', 'pt', 'sob', 'nd'];
const HOURS_PER_DAY = 24;

function bucketClass(value: number, max: number): string {
  if (value === 0) return 'bg-bg-elevated';
  const ratio = value / max;
  if (ratio < 0.2) return 'bg-accent/15';
  if (ratio < 0.4) return 'bg-accent/30';
  if (ratio < 0.6) return 'bg-accent/50';
  if (ratio < 0.8) return 'bg-accent/70';
  return 'bg-accent/90';
}

export function Heatmap({ cells }: HeatmapProps) {
  const grid: number[][] = Array.from({ length: 7 }, () => new Array(HOURS_PER_DAY).fill(0));
  for (const cell of cells) {
    const dayIdx = (cell.dayOfWeek ?? 1) - 1;
    if (dayIdx >= 0 && dayIdx < 7) {
      grid[dayIdx][cell.hour] = cell.count;
    }
  }
  const max = Math.max(1, ...cells.map((c) => c.count));

  return (
    <div className="space-y-1">
      <div className="grid grid-cols-[28px_repeat(24,1fr)] gap-[2px] text-[10px] text-text-dim mb-1">
        <span></span>
        {Array.from({ length: HOURS_PER_DAY }).map((_, h) => (
          <span key={h} className="text-center">{h % 4 === 0 ? h : ''}</span>
        ))}
      </div>
      {DAY_LABELS.map((day, dayIdx) => (
        <div key={day} className="grid grid-cols-[28px_repeat(24,1fr)] gap-[2px] items-center">
          <span className="text-[10px] text-text-dim">{day}</span>
          {grid[dayIdx].map((value, hour) => (
            <div
              key={hour}
              className={`aspect-square rounded-sm ${bucketClass(value, max)} hover:scale-125 transition-transform cursor-default relative group`}
              title={`${day} ${hour}:00 — ${value} wiadomości`}
            >
              {value > 0 && (
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 bg-bg-hover border border-border-strong px-2 py-1 rounded text-[11px] whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                  {day} {hour}:00 — {value} wiad.
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
      <div className="flex items-center gap-2 text-[11px] text-text-dim mt-2.5">
        <span>mniej</span>
        <div className="flex gap-[2px]">
          <div className="w-3.5 h-2.5 rounded-sm bg-bg-elevated" />
          <div className="w-3.5 h-2.5 rounded-sm bg-accent/15" />
          <div className="w-3.5 h-2.5 rounded-sm bg-accent/30" />
          <div className="w-3.5 h-2.5 rounded-sm bg-accent/50" />
          <div className="w-3.5 h-2.5 rounded-sm bg-accent/70" />
          <div className="w-3.5 h-2.5 rounded-sm bg-accent/90" />
        </div>
        <span>więcej</span>
      </div>
    </div>
  );
}
