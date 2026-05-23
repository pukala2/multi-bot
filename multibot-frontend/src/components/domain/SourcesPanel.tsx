import { FileText, Inbox } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { formatScore } from '@/lib/format';
import type { ChatSource } from '@/hooks/useStreamMessage';

interface SourcesPanelProps {
  sources: ChatSource[];
  loading?: boolean;
}

function scoreVariant(score: number): 'success' | 'info' | 'neutral' {
  if (score >= 0.6) return 'success';
  if (score >= 0.4) return 'info';
  return 'neutral';
}

export function SourcesPanel({ sources, loading }: SourcesPanelProps) {
  return (
    <Card className="h-full flex flex-col">
      <CardHeader>
        <div>
          <CardTitle>Źródła</CardTitle>
          <CardSubtitle>
            {sources.length === 0 ? 'fragmenty z dokumentów użyte w ostatniej odpowiedzi' : `${sources.length} fragmentów`}
          </CardSubtitle>
        </div>
      </CardHeader>
      <CardBody className="flex-1 overflow-y-auto space-y-3">
        {sources.length === 0 && !loading && (
          <div className="text-center py-12 text-text-muted">
            <Inbox className="size-8 mx-auto mb-2 opacity-40" />
            <div className="text-[13px]">Brak źródeł</div>
            <div className="text-[12px] text-text-dim mt-1">
              Zadaj pytanie aby zobaczyć z czego bot skorzystał.
            </div>
          </div>
        )}

        {sources.length === 0 && loading && (
          <div className="space-y-2">
            <div className="h-16 bg-bg-elevated/40 rounded animate-pulse" />
            <div className="h-16 bg-bg-elevated/40 rounded animate-pulse" />
          </div>
        )}

        {sources.map((source, idx) => (
          <div
            key={source.chunkId ?? idx}
            className="p-3 rounded-md bg-bg-elevated/50 border border-border hover:border-border-strong transition-colors"
          >
            <div className="flex items-center justify-between gap-2 mb-1.5">
              <div className="flex items-center gap-1.5 min-w-0">
                <FileText className="size-3.5 text-text-muted shrink-0" />
                <span className="text-[12.5px] font-medium text-text truncate">
                  {source.documentFilename ?? 'nieznany plik'}
                </span>
              </div>
              {source.score != null && (
                <Badge variant={scoreVariant(source.score)} className="shrink-0">
                  {formatScore(source.score)}
                </Badge>
              )}
            </div>
            {source.snippet && (
              <div className="text-[11.5px] text-text-muted leading-relaxed line-clamp-4">
                {source.snippet}
              </div>
            )}
          </div>
        ))}
      </CardBody>
    </Card>
  );
}
