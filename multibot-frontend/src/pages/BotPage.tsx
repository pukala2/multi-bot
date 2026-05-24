import { useEffect, useState } from 'react';
import { Plus, CheckCircle2, Trash2, Copy } from 'lucide-react';
import { toast } from 'sonner';
import { Topbar } from '@/components/layout/Topbar';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Dialog, DialogHeader, DialogBody, DialogFooter } from '@/components/ui/dialog';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { UploadZone } from '@/components/domain/UploadZone';
import { DocumentsTable } from '@/components/domain/DocumentsTable';
import { useDocuments } from '@/hooks/useDocuments';
import {
  useVersions,
  useCreateVersion,
  useActivateVersion,
  useDeleteVersion,
  useVersionDocuments,
} from '@/hooks/useVersions';
import { showApiError } from '@/api/errors';
import { timeAgo } from '@/lib/format';
import { cn } from '@/lib/cn';
import type { components } from '@/api/schema';

type VersionStatus = components['schemas']['ChatbotVersionResponse']['status'];

function statusVariant(status: VersionStatus): 'success' | 'warning' | 'neutral' {
  if (status === 'ACTIVE') return 'success';
  if (status === 'DRAFT') return 'warning';
  return 'neutral';
}

function statusLabel(status: VersionStatus): string {
  if (status === 'ACTIVE') return 'Aktywna';
  if (status === 'DRAFT') return 'Robocza';
  return 'Zarchiwizowana';
}

export function BotPage() {
  const versions = useVersions();
  const createMutation = useCreateVersion();
  const activateMutation = useActivateVersion();
  const deleteMutation = useDeleteVersion();

  const [selectedVersionId, setSelectedVersionId] = useState<string | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [copyFrom, setCopyFrom] = useState<string>('');

  useEffect(() => {
    if (versions.data && versions.data.length > 0 && !selectedVersionId) {
      const active = versions.data.find((v) => v.status === 'ACTIVE');
      setSelectedVersionId(active?.id ?? versions.data[0].id);
    }
  }, [versions.data, selectedVersionId]);

  const selectedVersion = versions.data?.find((v) => v.id === selectedVersionId) ?? null;
  const versionDocs = useVersionDocuments(selectedVersionId);
  const allDocs = useDocuments({ size: 100 });

  const docIdsInVersion = new Set(versionDocs.data ?? []);
  const visibleDocs = (allDocs.data?.content ?? []).filter((d) => docIdsInVersion.has(d.id ?? ''));

  const handleCreate = () => {
    if (!newName.trim()) {
      toast.error('Nazwa jest wymagana');
      return;
    }
    createMutation.mutate(
      { name: newName.trim(), copyFromVersionId: copyFrom || undefined },
      {
        onSuccess: (v) => {
          toast.success(`Utworzono wersję "${v.name}"`);
          setCreateDialogOpen(false);
          setNewName('');
          setCopyFrom('');
          setSelectedVersionId(v.id);
        },
        onError: (err) => showApiError(err, 'Nie udało się utworzyć wersji'),
      }
    );
  };

  const handleActivate = (id: string, name: string) => {
    activateMutation.mutate(id, {
      onSuccess: () => toast.success(`Aktywowano "${name}"`),
      onError: (err) => showApiError(err, 'Nie udało się aktywować'),
    });
  };

  const handleDelete = (id: string, name: string) => {
    if (!confirm(`Usunąć wersję "${name}"? Tej operacji nie da się cofnąć.`)) return;
    deleteMutation.mutate(id, {
      onSuccess: () => {
        toast.success(`Usunięto "${name}"`);
        if (selectedVersionId === id) {
          setSelectedVersionId(null);
        }
      },
      onError: (err) => showApiError(err, 'Nie udało się usunąć'),
    });
  };

  return (
    <>
      <Topbar
        title="Bot"
        actions={
          <Button onClick={() => setCreateDialogOpen(true)}>
            <Plus className="size-3.5" /> Nowa wersja
          </Button>
        }
      />

      <Card className="mb-4">
        <CardHeader>
          <div>
            <CardTitle>Wersje bota</CardTitle>
            <CardSubtitle>
              Każda wersja to zestaw dokumentów. Aktywuj wybraną żeby bot z niej korzystał.
            </CardSubtitle>
          </div>
        </CardHeader>

        {versions.isPending ? (
          <div className="p-3 space-y-2">
            {[1, 2].map((i) => <div key={i} className="h-12 bg-bg-elevated/40 rounded animate-pulse" />)}
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Nazwa</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Dokumentów</TableHead>
                <TableHead>Utworzono</TableHead>
                <TableHead>Akcje</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(versions.data ?? []).map((v) => (
                <TableRow
                  key={v.id}
                  className={cn(
                    'cursor-pointer',
                    selectedVersionId === v.id && 'bg-bg-elevated/40'
                  )}
                  onClick={() => setSelectedVersionId(v.id)}
                >
                  <TableCell className="font-medium">{v.name}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(v.status)} withDot>
                      {statusLabel(v.status)}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-text-muted">{v.documentCount}</TableCell>
                  <TableCell className="text-text-muted">{timeAgo(v.createdAt)}</TableCell>
                  <TableCell>
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      {v.status !== 'ACTIVE' && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleActivate(v.id, v.name)}
                          disabled={activateMutation.isPending}
                          className="text-success hover:text-success"
                        >
                          <CheckCircle2 className="size-3.5" /> Aktywuj
                        </Button>
                      )}
                      {v.status !== 'ACTIVE' && (
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDelete(v.id, v.name)}
                          disabled={deleteMutation.isPending}
                          className="text-text-muted hover:text-error"
                          aria-label="Usuń"
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {selectedVersion && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <h2 className="text-[16px] font-semibold">
              Dokumenty w „{selectedVersion.name}"
            </h2>
            <Badge variant={statusVariant(selectedVersion.status)} withDot>
              {statusLabel(selectedVersion.status)}
            </Badge>
          </div>

          {selectedVersion.status !== 'ARCHIVED' && <UploadZone />}

          <Card>
            <CardHeader>
              <div>
                <CardTitle>Lista dokumentów</CardTitle>
                <CardSubtitle>
                  {versionDocs.data?.length ?? '—'} dokumentów w tej wersji
                </CardSubtitle>
              </div>
            </CardHeader>
            <DocumentsTable
              documents={visibleDocs}
              loading={versionDocs.isFetching || allDocs.isFetching}
              sort="uploadedAt,desc"
              onSortChange={() => {}}
            />
          </Card>
        </div>
      )}

      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogHeader title="Nowa wersja bota" onClose={() => setCreateDialogOpen(false)} />
        <DialogBody className="space-y-3">
          <div>
            <label className="text-[12.5px] text-text-muted block mb-1.5">Nazwa wersji</label>
            <Input
              value={newName}
              onChange={(e) => setNewName((e.target as HTMLInputElement).value)}
              placeholder="np. Wersja 2 - dodaję regulamin"
              maxLength={120}
              autoFocus
            />
          </div>
          <div>
            <label className="text-[12.5px] text-text-muted block mb-1.5">
              <Copy className="inline size-3 mr-1" />
              Kopiuj dokumenty z (opcjonalne)
            </label>
            <select
              value={copyFrom}
              onChange={(e) => setCopyFrom(e.target.value)}
              className="w-full h-8 rounded-md border border-border bg-bg-panel px-3 text-[13px]"
            >
              <option value="">— pusta wersja —</option>
              {(versions.data ?? []).map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} ({v.documentCount} dokumentów)
                </option>
              ))}
            </select>
          </div>
        </DialogBody>
        <DialogFooter>
          <Button variant="secondary" onClick={() => setCreateDialogOpen(false)}>Anuluj</Button>
          <Button onClick={handleCreate} disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Tworzę…' : 'Utwórz'}
          </Button>
        </DialogFooter>
      </Dialog>
    </>
  );
}
