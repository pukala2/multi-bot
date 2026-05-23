interface DocumentStatusBarProps {
  byStatus: Record<string, number>;
}

const SEGMENTS: Array<{ status: string; color: string; label: string }> = [
  { status: 'READY', color: 'bg-success', label: 'Gotowe' },
  { status: 'PROCESSING', color: 'bg-warning', label: 'Przetwarzanie' },
  { status: 'PENDING', color: 'bg-info', label: 'Oczekuje' },
  { status: 'FAILED', color: 'bg-error', label: 'Błąd' },
];

export function DocumentStatusBar({ byStatus }: DocumentStatusBarProps) {
  const total = Object.values(byStatus).reduce((sum, n) => sum + n, 0);
  if (total === 0) return null;

  return (
    <div className="space-y-1.5">
      <div className="flex h-1.5 rounded-full overflow-hidden bg-bg-elevated">
        {SEGMENTS.map((seg) => {
          const count = byStatus[seg.status] ?? 0;
          if (count === 0) return null;
          const percent = (count / total) * 100;
          return (
            <div
              key={seg.status}
              className={seg.color}
              style={{ width: `${percent}%` }}
              title={`${seg.label}: ${count}`}
            />
          );
        })}
      </div>
      <div className="flex gap-3 flex-wrap text-[10.5px] text-text-dim">
        {SEGMENTS.map((seg) => {
          const count = byStatus[seg.status] ?? 0;
          if (count === 0) return null;
          return (
            <span key={seg.status} className="flex items-center gap-1">
              <span className={`size-1.5 rounded-full ${seg.color}`} />
              {seg.label} {count}
            </span>
          );
        })}
      </div>
    </div>
  );
}
