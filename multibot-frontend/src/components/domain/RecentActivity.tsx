import { Link } from 'react-router-dom';
import { FileText, MessageSquare, Inbox } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { useDocuments } from '@/hooks/useDocuments';
import { useSessions } from '@/hooks/useSessions';
import { timeAgo, shortUuid } from '@/lib/format';

interface ActivityItem {
  kind: 'document' | 'session';
  id: string;
  label: string;
  detail: string;
  timestamp: string;
  to: string;
}

export function RecentActivity() {
  const documents = useDocuments({ size: 5 });
  const sessions = useSessions({ size: 5 });

  const loading = documents.isPending || sessions.isPending;

  const items: ActivityItem[] = [
    ...(documents.data?.content ?? []).map((doc) => ({
      kind: 'document' as const,
      id: doc.id ?? '',
      label: `Wgrano ${doc.filename ?? '(plik)'}`,
      detail: shortUuid(doc.id ?? ''),
      timestamp: doc.uploadedAt ?? '',
      to: '/documents',
    })),
    ...(sessions.data?.content ?? []).map((s) => ({
      kind: 'session' as const,
      id: s.id ?? '',
      label: `Sesja ${shortUuid(s.id ?? '', 8, 4)}`,
      detail: `${s.messageCount ?? 0} wiadomości`,
      timestamp: s.lastActivityAt ?? '',
      to: `/sessions/${s.id}`,
    })),
  ]
    .filter((i) => i.timestamp)
    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
    .slice(0, 8);

  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Ostatnia aktywność</CardTitle>
          <CardSubtitle>najnowsze uploady i sesje czatu</CardSubtitle>
        </div>
      </CardHeader>
      <CardBody className="space-y-2">
        {loading && (
          <div className="space-y-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-10 bg-bg-elevated/40 rounded animate-pulse" />
            ))}
          </div>
        )}

        {!loading && items.length === 0 && (
          <div className="py-8 text-center">
            <Inbox className="size-6 mx-auto mb-2 text-text-dim" />
            <div className="text-text-muted text-[13px]">Brak aktywności.</div>
          </div>
        )}

        {items.map((item) => (
          <Link
            key={`${item.kind}-${item.id}`}
            to={item.to}
            className="flex items-center gap-3 px-3 py-2 rounded-md hover:bg-bg-elevated/50 transition-colors"
          >
            <div
              className={`size-7 rounded-md flex items-center justify-center shrink-0 ${
                item.kind === 'document' ? 'bg-info/15 text-info' : 'bg-accent/15 text-accent'
              }`}
            >
              {item.kind === 'document' ? (
                <FileText className="size-3.5" />
              ) : (
                <MessageSquare className="size-3.5" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-[13px] text-text truncate">{item.label}</div>
              <div className="text-[11.5px] text-text-dim mono">{item.detail}</div>
            </div>
            <div className="text-[11.5px] text-text-dim shrink-0">{timeAgo(item.timestamp)}</div>
          </Link>
        ))}
      </CardBody>
    </Card>
  );
}
