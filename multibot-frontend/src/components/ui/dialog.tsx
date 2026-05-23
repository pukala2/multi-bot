import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '@/lib/cn';

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}

export function Dialog({ open, onOpenChange, children }: DialogProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onOpenChange(false);
    };
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onOpenChange]);

  if (!open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm animate-in fade-in"
      onClick={() => onOpenChange(false)}
    >
      <div
        className="bg-bg-panel border border-border rounded-lg shadow-2xl max-w-md w-full mx-4 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>,
    document.body
  );
}

interface DialogHeaderProps {
  title: string;
  description?: string;
  onClose: () => void;
}

export function DialogHeader({ title, description, onClose }: DialogHeaderProps) {
  return (
    <div className="px-5 py-4 border-b border-border flex justify-between items-start gap-3">
      <div>
        <h2 className="text-[15px] font-semibold">{title}</h2>
        {description && <p className="text-[12.5px] text-text-muted mt-1">{description}</p>}
      </div>
      <button
        type="button"
        onClick={onClose}
        className="text-text-muted hover:text-text size-7 flex items-center justify-center rounded hover:bg-bg-elevated transition-colors -mt-1 -mr-1"
        aria-label="Zamknij"
      >
        <X className="size-4" />
      </button>
    </div>
  );
}

interface DialogBodyProps {
  children: ReactNode;
  className?: string;
}

export function DialogBody({ children, className }: DialogBodyProps) {
  return <div className={cn('p-5', className)}>{children}</div>;
}

interface DialogFooterProps {
  children: ReactNode;
}

export function DialogFooter({ children }: DialogFooterProps) {
  return <div className="px-5 py-4 border-t border-border flex justify-end gap-2">{children}</div>;
}
