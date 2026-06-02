import { useRef, useState, type DragEvent } from 'react';
import { Upload, CheckCircle2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardBody } from '@/components/ui/card';
import { useUploadDocument } from '@/hooks/useUploadDocument';
import { useDocumentFilenames } from '@/hooks/useDocumentFilenames';
import { showApiError } from '@/api/errors';
import { formatBytes, plPlural } from '@/lib/format';
import { cn } from '@/lib/cn';

const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.txt', '.md'];
const ACCEPTED_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'text/markdown',
];
const MAX_SIZE_BYTES = 50 * 1024 * 1024;
const PLIK_FORMS: [string, string, string] = ['plik', 'pliki', 'plików'];

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

interface UploadZoneProps {
  versionId?: string;
}

interface Batch {
  done: number;
  total: number;
  current: string;
}

export function UploadZone({ versionId }: UploadZoneProps = {}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [batch, setBatch] = useState<Batch | null>(null);
  const [progress, setProgress] = useState(0);

  const upload = useUploadDocument();
  const filenames = useDocumentFilenames(versionId);

  // Wgrywa wiele plików po kolei. Duplikaty (po nazwie) i niepoprawne pliki pomijane,
  // na końcu jedno podsumowanie. Świadoma decyzja: przy paczce nie pytamy per plik.
  const handleFiles = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    if (!filenames.data) {
      toast.error('Sprawdzanie duplikatów…', { description: 'Lista dokumentów jeszcze się ładuje. Spróbuj za chwilę.' });
      return;
    }

    const files = Array.from(fileList);
    const invalid: string[] = [];
    const duplicates: string[] = [];
    const toUpload: File[] = [];

    for (const f of files) {
      if (validateFile(f)) { invalid.push(f.name); continue; }
      if (filenames.data.has(f.name) || toUpload.some((t) => t.name === f.name)) { duplicates.push(f.name); continue; }
      toUpload.push(f);
    }

    let uploaded = 0;
    if (toUpload.length > 0) {
      setBatch({ done: 0, total: toUpload.length, current: toUpload[0].name });
      for (let i = 0; i < toUpload.length; i++) {
        const f = toUpload[i];
        setBatch({ done: i, total: toUpload.length, current: f.name });
        setProgress(0);
        try {
          await upload.mutateAsync({ file: f, versionId, onProgress: setProgress });
          uploaded++;
        } catch (error) {
          showApiError(error, `Nie udało się wgrać „${f.name}"`);
        }
      }
      setBatch(null);
      setProgress(0);
    }

    // podsumowanie
    if (uploaded > 0) {
      toast.success(`Wgrano ${uploaded} ${plPlural(uploaded, PLIK_FORMS)}`, {
        description: 'Trwa indeksowanie. Status zaktualizuje się automatycznie.',
      });
    }
    const skipped: string[] = [];
    if (duplicates.length) skipped.push(`${duplicates.length} ${plPlural(duplicates.length, PLIK_FORMS)} (już istnieją)`);
    if (invalid.length) skipped.push(`${invalid.length} ${plPlural(invalid.length, PLIK_FORMS)} (zły format/rozmiar)`);
    if (skipped.length) {
      toast.warning(`Pominięto: ${skipped.join(', ')}`, {
        description: 'Aby zastąpić istniejący dokument, najpierw go usuń.',
      });
    }
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    void handleFiles(e.dataTransfer.files);
  };

  const onDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(true);
  };

  const onDragLeave = () => setDragOver(false);

  const isUploading = batch !== null;

  return (
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
            multiple
            accept={ACCEPTED_MIME_TYPES.join(',') + ',' + ACCEPTED_EXTENSIONS.join(',')}
            className="hidden"
            onChange={(e) => {
              void handleFiles(e.target.files);
              e.target.value = '';
            }}
          />

          {!isUploading && (
            <>
              <div className="size-12 mx-auto mb-3 rounded-full bg-bg-elevated flex items-center justify-center text-accent">
                <Upload className="size-5" />
              </div>
              <div className="text-[14px] font-medium text-text mb-1">
                Przeciągnij pliki tutaj albo
              </div>
              <Button variant="secondary" onClick={() => inputRef.current?.click()} className="mt-2">
                Wybierz pliki
              </Button>
              <div className="text-[12px] text-text-muted mt-3">
                Możesz wybrać wiele naraz · .pdf, .docx, .txt, .md · maks. 50 MB każdy
              </div>
              {upload.isSuccess && (
                <div className="flex items-center justify-center gap-2 text-success text-[13px] mt-3">
                  <CheckCircle2 className="size-4" /> Gotowe
                </div>
              )}
            </>
          )}

          {isUploading && batch && (
            <div className="space-y-3">
              <div className="size-12 mx-auto rounded-full bg-bg-elevated flex items-center justify-center text-accent animate-pulse">
                <Upload className="size-5" />
              </div>
              <div className="text-[13px] text-text">
                Wgrywanie {batch.done + 1}/{batch.total}: <span className="text-text-muted">{batch.current}</span> · {progress}%
              </div>
              <div className="h-1.5 bg-bg-elevated rounded-full overflow-hidden mx-auto max-w-[300px]">
                <div className="h-full bg-accent transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}
        </div>
      </CardBody>
    </Card>
  );
}
