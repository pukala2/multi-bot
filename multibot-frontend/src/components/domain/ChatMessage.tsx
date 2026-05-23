import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Sparkles, User } from 'lucide-react';
import { cn } from '@/lib/cn';

export type ChatRole = 'user' | 'assistant';

interface ChatMessageProps {
  role: ChatRole;
  content: string;
  streaming?: boolean;
  tokensUsed?: number | null;
}

export function ChatMessage({ role, content, streaming, tokensUsed }: ChatMessageProps) {
  const isUser = role === 'user';

  return (
    <div className={cn('flex gap-3 px-1', isUser && 'flex-row-reverse')}>
      <div
        className={cn(
          'size-8 rounded-md flex items-center justify-center shrink-0 mt-0.5',
          isUser ? 'bg-bg-elevated text-text-muted' : 'bg-accent/15 text-accent'
        )}
      >
        {isUser ? <User className="size-4" /> : <Sparkles className="size-4" />}
      </div>

      <div className={cn('max-w-[80%]', isUser && 'text-right')}>
        <div className="text-[11px] uppercase tracking-wider font-semibold text-text-dim mb-1">
          {isUser ? 'Ty' : 'Bot'}
        </div>
        <div
          className={cn(
            'inline-block text-left rounded-lg px-4 py-2.5 text-[13.5px] leading-relaxed',
            isUser
              ? 'bg-bg-elevated text-text'
              : 'bg-bg-panel border border-border text-text'
          )}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap m-0">{content}</p>
          ) : (
            <div className="markdown">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content || (streaming ? '' : '_(pusta odpowiedź)_')}
              </ReactMarkdown>
              {streaming && content && (
                <span className="inline-block w-1 h-4 bg-accent ml-0.5 animate-pulse align-middle" />
              )}
              {streaming && !content && (
                <div className="flex gap-1 py-1">
                  <span className="size-1.5 rounded-full bg-text-muted animate-bounce" style={{ animationDelay: '0ms' }} />
                  <span className="size-1.5 rounded-full bg-text-muted animate-bounce" style={{ animationDelay: '150ms' }} />
                  <span className="size-1.5 rounded-full bg-text-muted animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              )}
            </div>
          )}
        </div>
        {!isUser && tokensUsed != null && !streaming && (
          <div className="text-[11px] text-text-dim mt-1">Użyto {tokensUsed} tokenów</div>
        )}
      </div>
    </div>
  );
}
