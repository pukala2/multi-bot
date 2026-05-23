import { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardBody } from '@/components/ui/card';
import { ChatMessage } from '@/components/domain/ChatMessage';
import { useSessionHistory } from '@/hooks/useSessionHistory';
import { ApiError } from '@/api/errors';
import { formatDateTime, formatTime, shortUuid } from '@/lib/format';

export function SessionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const history = useSessionHistory(id ?? '');

  useEffect(() => {
    if (history.error instanceof ApiError && history.error.status === 404) {
      toast.error('Sesja nie istnieje');
      navigate('/sessions');
    }
  }, [history.error, navigate]);

  const messages = history.data?.content ?? [];
  const totalTokens = messages.reduce((sum, msg) => sum + (msg.tokensUsed ?? 0), 0);
  const firstMessage = messages[0];

  return (
    <>
      <Topbar
        title="Sesja"
        actions={
          <Link to="/sessions">
            <Button variant="secondary">
              <ArrowLeft className="size-3.5" /> Wstecz
            </Button>
          </Link>
        }
      />

      <Card className="mb-4">
        <CardBody>
          {history.isPending ? (
            <div className="space-y-2">
              <div className="h-4 w-64 bg-bg-elevated/50 rounded animate-pulse" />
              <div className="h-3 w-96 bg-bg-elevated/40 rounded animate-pulse" />
            </div>
          ) : (
            <>
              <div className="text-[13px] mono text-text-muted mb-2">
                ID sesji: <span className="text-text">{id}</span>
              </div>
              <div className="grid grid-cols-3 gap-4 text-[12.5px]">
                <div>
                  <div className="text-text-dim mb-0.5">Rozpoczęta</div>
                  <div className="text-text">
                    {firstMessage?.createdAt ? formatDateTime(firstMessage.createdAt) : '—'}
                  </div>
                </div>
                <div>
                  <div className="text-text-dim mb-0.5">Liczba wiadomości</div>
                  <div className="text-text font-medium">{messages.length}</div>
                </div>
                <div>
                  <div className="text-text-dim mb-0.5">Łącznie tokenów</div>
                  <div className="text-text font-medium">{totalTokens || '—'}</div>
                </div>
              </div>
            </>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardBody className="space-y-5">
          {history.isPending && (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-16 bg-bg-elevated/30 rounded animate-pulse" />
              ))}
            </div>
          )}

          {!history.isPending && messages.length === 0 && (
            <div className="py-12 text-center">
              <div className="text-text-muted text-[14px]">Sesja jest pusta.</div>
              <div className="text-text-dim text-[12.5px] mt-1">
                W tej rozmowie nie ma jeszcze wiadomości.
              </div>
            </div>
          )}

          {messages.map((msg) => {
            const role = (msg.role ?? '').toLowerCase() === 'user' ? 'user' : 'assistant';
            return (
              <div key={msg.id}>
                <ChatMessage
                  role={role}
                  content={msg.content ?? ''}
                  tokensUsed={role === 'assistant' ? msg.tokensUsed ?? null : undefined}
                />
                {msg.createdAt && (
                  <div className={`text-[11px] text-text-dim mt-1 ${role === 'user' ? 'text-right pr-12' : 'pl-12'}`}>
                    {formatTime(msg.createdAt)}
                    {msg.id && (
                      <span className="mono ml-2">· {shortUuid(msg.id, 6, 4)}</span>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </CardBody>
      </Card>
    </>
  );
}
