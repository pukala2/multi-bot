import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { AlertTriangle } from 'lucide-react';

interface DeleteConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  filename: string;
  isDeleting: boolean;
}

export function DeleteConfirmDialog({
  open,
  onClose,
  onConfirm,
  filename,
  isDeleting,
}: DeleteConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogHeader title="Usunąć dokument?" onClose={onClose} />
      <DialogBody>
        <div className="flex gap-3">
          <div className="size-10 rounded-full bg-error/15 flex items-center justify-center text-error shrink-0">
            <AlertTriangle className="size-4" />
          </div>
          <div className="text-[13.5px] text-text-muted">
            Plik <span className="text-text font-medium">{filename}</span> zostanie usunięty,
            a bot przestanie z niego korzystać. <span className="text-error">Tej operacji nie da się cofnąć.</span>
          </div>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button variant="secondary" onClick={onClose} disabled={isDeleting}>
          Anuluj
        </Button>
        <Button variant="danger" onClick={onConfirm} disabled={isDeleting}>
          {isDeleting ? 'Usuwam…' : 'Usuń'}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
