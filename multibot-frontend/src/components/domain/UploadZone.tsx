import { useRef, useState, type DragEvent } from 'react';
import { Upload, CheckCircle2, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardBody } from '@/components/ui/card';
import { OverwriteConfirmDialog } from './OverwriteConfirmDialog';
import { useUploadDocument } from '@/hooks/useUploadDocument';
import { useDeleteDocument } from '@/hooks/useDeleteDocument';
import { useDocumentFilenames } from '@/hooks/useDocumentFilenames';
import { showApiError } from '@/api/errors';
import { formatBytes } from '@/lib/format';
import { cn } from '@/lib/cn';

const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.txt', '.md'];
const ACCEPTED_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'text/markdown',
];
const MAX_SIZE_BYTES = 50 * 1024 * 1024;

function validateFile(file: File): string | null {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase();
  if (!ACCEPTED_EXTENSIONS.includes(ext)) {
    return `Niedozwolone rozszerzenie. Dozwolone: ${ACCEPTED_EXTENSIONS.join(', ')}`;
  }
  if (file.size > MAX_SIZE_BYTES) {
    return `Plik przekracza limit 50 MB (ma ${formatBytes(file.size)})`;
  }
  return null;
}

export function UploadZone() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [conflict, setConflict] = useState<{ file: File; existingId: string } | null>(null);
  const [overwriting, setOverwriting] = useState(false);

  const upload = useUploadDocument();
  const remove = useDeleteDocument();
  const filenames = useDocumentFilenames();

  const performUpload = (file: File) => {
    setProgress(0);
    upload.mutate(
      { file, onProgress: setProgress },
      {
        onSuccess: (doc) => {
          toast.success(`Wgrano "${doc.filename}"`, {
            description: 'Trwa indeksowanie. Status zaktualizuje się automatycznie.',
          });
          setProgress(null);
        },
        onError: (error) => {
          showApiError(error, 'Nie udało się wgrać pliku');
          setProgress(null);
        },
      }
    );
  };

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const file = files[0];
    const error = validateFile(file);
    if (error) {
      toast.error('Nie można wgrać pliku', { description: error });
      return;
    }

    if (filenames.isError) {
      showApiError(filenames.error, 'Nie udało się sprawdzić istniejących dokumentów');
      return;
    }
    if (!filenames.data) {
      toast.error('Sprawdzanie duplikatów…', {
        description: 'Lista dokumentów jeszcze się ładuje. Spróbuj za chwilę.',
      });
      return;
    }

    const existingId = filenames.data.get(file.name);
    if (existingId) {
      setConflict({ file, existingId });
      return;
    }

    performUpload(file);
  };

  const handleOverwrite = async () => {
    if (!conflict) return;
    setOverwriting(true);
    try {
      await remove.mutateAsync(conflict.existingId);
      performUpload(conflict.file);
      setConflict(null);
    } catch (error) {
      showApiError(error, 'Nie udało się usunąć istniejącego dokumentu');
    } finally {
      setOverwriting(false);
    }
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    handleFiles(e.dataTransfer.files);
  };

  const onDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(true);
  };

  const onDragLeave = () => setDragOver(false);

  const isUploading = upload.isPending || progress !== null;

  return (
    <>
      <Card>
        <CardBody>
          <div
            onDrop={onDrop}
            onDragOver={onDragOver}
            onDragLeave={onDragLeave}
            className={cn(
              'border-2 border-dashed rounded-lg py-10 px-6 text-center transition-colors',
              dragOver
                ? 'border-accent bg-accent/5'
                : 'border-border-strong hover:border-accent/40 hover:bg-bg-elevated/40'
            )}
          >
            <input
              ref={inputRef}
              type="file"
              accept={ACCEPTED_MIME_TYPES.join(',') + ',' + ACCEPTED_EXTENSIONS.join(',')}
              className="hidden"
              onChange={(e) => {
                handleFiles(e.target.files);
                e.target.value = '';
              }}
            />

            {!isUploading && (
              <>
                <div className="size-12 mx-auto mb-3 rounded-full bg-bg-elevated flex items-center justify-center text-accent">
                  <Upload className="size-5" />
                </div>
                <div className="text-[14px] font-medium text-text mb-1">
                  Przeciągnij plik tutaj albo
                </div>
                <Button variant="secondary" onClick={() => inputRef.current?.click()} className="mt-2">
                  Wybierz plik
                </Button>
                <div className="text-[12px] text-text-muted mt-3">
                  Dozwolone: .pdf, .docx, .txt, .md · maks. 50 MB
                </div>
              </>
            )}

            {isUploading && progress !== null && (
              <div className="space-y-3">
                <div className="size-12 mx-auto rounded-full bg-bg-elevated flex items-center justify-center text-accent animate-pulse">
                  <Upload className="size-5" />
                </div>
                <div className="text-[13px] text-text">Wgrywanie… {progress}%</div>
                <div className="h-1.5 bg-bg-elevated rounded-full overflow-hidden mx-auto max-w-[300px]">
                  <div
                    className="h-full bg-accent transition-all"
                    style={{ width: `${progress}%` }}
                  />
                </div>
              </div>
            )}

            {upload.isSuccess && !isUploading && (
              <div className="flex items-center justify-center gap-2 text-success text-[13px] mt-2">
                <CheckCircle2 className="size-4" />
                Wgrano
              </div>
            )}

            {upload.isError && !isUploading && (
              <div className="flex items-center justify-center gap-2 text-error text-[13px] mt-2">
                <AlertCircle className="size-4" />
                Spróbuj ponownie
              </div>
            )}
          </div>
        </CardBody>
      </Card>

      <OverwriteConfirmDialog
        open={!!conflict}
        onClose={() => setConflict(null)}
        onConfirm={handleOverwrite}
        filename={conflict?.file.name ?? ''}
        isProcessing={overwriting}
      />
    </>
  );
}
