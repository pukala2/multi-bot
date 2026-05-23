import { useState, type KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';

interface ChatInputProps {
  onSend: (content: string) => void;
  disabled: boolean;
  placeholder?: string;
}

export function ChatInput({ onSend, disabled, placeholder }: ChatInputProps) {
  const [value, setValue] = useState('');

  const send = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue('');
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <div className="border-t border-border p-3 bg-bg-base">
      <div className="flex gap-2 items-end">
        <Textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={placeholder ?? 'Zadaj pytanie… (Enter wysyła, Shift+Enter nowa linia)'}
          rows={2}
          disabled={disabled}
          className="flex-1"
        />
        <Button onClick={send} disabled={disabled || !value.trim()} size="md" className="h-[60px] px-4">
          <Send className="size-4" />
        </Button>
      </div>
    </div>
  );
}
