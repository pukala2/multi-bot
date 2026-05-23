import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, Inbox } from 'lucide-react';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { PaginationFooter } from '@/components/domain/PaginationFooter';
import { useSessions } from '@/hooks/useSessions';
import { shortUuid, timeAgo } from '@/lib/format';

export function SessionsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const navigate = useNavigate();

  const sessions = useSessions({ page, size });
  const data = sessions.data;
  const items = data?.content ?? [];

  return (
    <>
      <Topbar title="Sesje" />

      <Card>
        <CardHeader>
          <div>
            <CardTitle>Lista sesji</CardTitle>
            <CardSubtitle>
              {data?.pagination?.totalElements ?? '—'}{' '}
              {(data?.pagination?.totalElements ?? 0) === 1 ? 'rozmowa' : 'rozmów'} w bazie
            </CardSubtitle>
          </div>
        </CardHeader>

        {sessions.isPending && items.length === 0 ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-12 bg-bg-elevated/40 rounded animate-pulse" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="py-16 text-center">
            <Inbox className="size-8 mx-auto mb-2 text-text-dim" />
            <div className="text-text-muted text-[14px]">Brak sesji.</div>
            <div className="text-text-dim text-[12.5px] mt-1">
              Pierwsza rozmowa pojawi się gdy ktoś użyje bota.
            </div>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Anonimowy ID</TableHead>
                <TableHead>Wiadomości</TableHead>
                <TableHead>Ostatnia aktywność</TableHead>
                <TableHead>ID sesji</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((session) => (
                <TableRow
                  key={session.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/sessions/${session.id}`)}
                >
                  <TableCell className="mono text-text-muted">
                    {session.anonymousId ? shortUuid(session.anonymousId, 12, 4) : '—'}
                  </TableCell>
                  <TableCell>
                    <span className="text-text font-medium">{session.messageCount ?? 0}</span>
                  </TableCell>
                  <TableCell className="text-text-muted">
                    {session.lastActivityAt ? timeAgo(session.lastActivityAt) : '—'}
                  </TableCell>
                  <TableCell className="mono text-text-muted">{shortUuid(session.id ?? '')}</TableCell>
                  <TableCell className="text-right">
                    <ChevronRight className="size-4 text-text-dim ml-auto" />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {data && items.length > 0 && (
          <PaginationFooter
            page={page}
            size={size}
            totalPages={data.pagination?.totalPages ?? 1}
            totalElements={data.pagination?.totalElements ?? 0}
            onPageChange={setPage}
            onSizeChange={(s) => {
              setSize(s);
              setPage(0);
            }}
          />
        )}
      </Card>
    </>
  );
}
