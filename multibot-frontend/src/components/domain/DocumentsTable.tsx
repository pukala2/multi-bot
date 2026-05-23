import { useState } from 'react';
import { Trash2, ArrowUpDown, ArrowUp, ArrowDown } from 'lucide-react';
import { toast } from 'sonner';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DeleteConfirmDialog } from './DeleteConfirmDialog';
import { useDeleteDocument } from '@/hooks/useDeleteDocument';
import { showApiError } from '@/api/errors';
import { documentStatusLabel, documentStatusVariant } from '@/lib/labels';
import { formatBytes, shortUuid, timeAgo } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { components } from '@/api/schema';

type DocumentSummary = components['schemas']['DocumentSummary'];

interface DocumentsTableProps {
  documents: DocumentSummary[];
  loading: boolean;
  sort: string;
  onSortChange: (sort: string) => void;
}

const COLUMNS: Array<{ key: string; label: string; sortable: boolean }> = [
  { key: 'filename', label: 'Plik', sortable: true },
  { key: 'sizeBytes', label: 'Rozmiar', sortable: true },
  { key: 'status', label: 'Status', sortable: true },
  { key: 'uploadedAt', label: 'Wgrane', sortable: true },
  { key: 'id', label: 'ID', sortable: false },
  { key: '__actions', label: '', sortable: false },
];

function parseSort(sort: string): { field: string; dir: 'asc' | 'desc' } {
  const [field = 'uploadedAt', dir = 'desc'] = sort.split(',');
  return { field, dir: dir as 'asc' | 'desc' };
}

function buildSort(field: string, currentSort: string): string {
  const { field: cur, dir } = parseSort(currentSort);
  if (cur !== field) return `${field},desc`;
  return `${field},${dir === 'desc' ? 'asc' : 'desc'}`;
}

export function DocumentsTable({ documents, loading, sort, onSortChange }: DocumentsTableProps) {
  const [toDelete, setToDelete] = useState<DocumentSummary | null>(null);
  const deleteMutation = useDeleteDocument();

  const handleDelete = () => {
    if (!toDelete?.id) return;
    deleteMutation.mutate(toDelete.id, {
      onSuccess: () => {
        toast.success(`Usunięto "${toDelete.filename}"`);
        setToDelete(null);
      },
      onError: (error) => showApiError(error, 'Nie udało się usunąć dokumentu'),
    });
  };

  const { field: sortField, dir: sortDir } = parseSort(sort);

  if (loading && documents.length === 0) {
    return (
      <div className="space-y-2 py-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-12 bg-bg-elevated/40 rounded animate-pulse" />
        ))}
      </div>
    );
  }

  if (documents.length === 0) {
    return (
      <div className="py-16 text-center">
        <div className="text-text-muted text-[14px]">Brak dokumentów.</div>
        <div className="text-text-dim text-[12.5px] mt-1">Wgraj pierwszy plik aby zacząć indeksowanie.</div>
      </div>
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            {COLUMNS.map((col) => (
              <TableHead key={col.key}>
                {col.sortable ? (
                  <button
                    type="button"
                    onClick={() => onSortChange(buildSort(col.key, sort))}
                    className="inline-flex items-center gap-1 hover:text-text transition-colors"
                  >
                    {col.label}
                    {sortField === col.key ? (
                      sortDir === 'desc' ? (
                        <ArrowDown className="size-3" />
                      ) : (
                        <ArrowUp className="size-3" />
                      )
                    ) : (
                      <ArrowUpDown className="size-3 opacity-40" />
                    )}
                  </button>
                ) : (
                  col.label
                )}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {documents.map((doc) => (
            <TableRow key={doc.id} className={cn(loading && 'opacity-60')}>
              <TableCell className="font-medium">{doc.filename}</TableCell>
              <TableCell className="text-text-muted">{formatBytes(doc.sizeBytes ?? 0)}</TableCell>
              <TableCell>
                <Badge variant={documentStatusVariant(doc.status ?? '')} withDot>
                  {documentStatusLabel(doc.status ?? '')}
                </Badge>
              </TableCell>
              <TableCell className="text-text-muted">{timeAgo(doc.uploadedAt ?? '')}</TableCell>
              <TableCell className="mono text-text-muted">{shortUuid(doc.id ?? '')}</TableCell>
              <TableCell className="text-right">
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => setToDelete(doc)}
                  aria-label={`Usuń ${doc.filename}`}
                  className="text-text-muted hover:text-error"
                >
                  <Trash2 className="size-3.5" />
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <DeleteConfirmDialog
        open={!!toDelete}
        onClose={() => setToDelete(null)}
        onConfirm={handleDelete}
        filename={toDelete?.filename ?? ''}
        isDeleting={deleteMutation.isPending}
      />
    </>
  );
}
