import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { RefreshCw } from 'lucide-react';

interface OverwriteConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  filename: string;
  isProcessing: boolean;
}

export function OverwriteConfirmDialog({
  open,
  onClose,
  onConfirm,
  filename,
  isProcessing,
}: OverwriteConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(v) => !v && !isProcessing && onClose()}>
      <DialogHeader title="Plik już istnieje" onClose={onClose} />
      <DialogBody>
        <div className="flex gap-3">
          <div className="size-10 rounded-full bg-warning/15 flex items-center justify-center text-warning shrink-0">
            <RefreshCw className="size-4" />
          </div>
          <div className="text-[13.5px] text-text-muted space-y-2">
            <p>
              Plik <span className="text-text font-medium">{filename}</span> już istnieje.
            </p>
            <p>
              Czy chcesz <span className="text-warning">zastąpić go nową wersją</span>?
              Stary plik zostanie usunięty, a bot zacznie korzystać z nowego.
            </p>
            <p className="text-text-dim text-[12.5px]">
              Tej operacji nie da się cofnąć.
            </p>
          </div>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button variant="secondary" onClick={onClose} disabled={isProcessing}>
          Anuluj
        </Button>
        <Button variant="primary" onClick={onConfirm} disabled={isProcessing}>
          {isProcessing ? 'Nadpisuję…' : 'Nadpisz'}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
