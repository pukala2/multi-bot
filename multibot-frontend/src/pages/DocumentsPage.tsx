import { useState } from 'react';
import { Topbar } from '@/components/layout/Topbar';
import { Card, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { UploadZone } from '@/components/domain/UploadZone';
import { DocumentsTable } from '@/components/domain/DocumentsTable';
import { PaginationFooter } from '@/components/domain/PaginationFooter';
import { useDocuments } from '@/hooks/useDocuments';
import { plPlural } from '@/lib/format';

const DOKUMENT_FORMS: [string, string, string] = ['dokument', 'dokumenty', 'dokumentów'];

export function DocumentsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState('uploadedAt,desc');

  const documents = useDocuments({ page, size, sort });
  const data = documents.data;
  const items = data?.content ?? [];

  return (
    <>
      <Topbar title="Dokumenty" />

      <div className="space-y-4">
        <UploadZone />

        <Card>
          <CardHeader>
            <div>
              <CardTitle>Lista dokumentów</CardTitle>
              <CardSubtitle>
                {data?.pagination?.totalElements ?? '—'} {plPlural(data?.pagination?.totalElements ?? 0, DOKUMENT_FORMS)} w bazie
              </CardSubtitle>
            </div>
          </CardHeader>

          <DocumentsTable
            documents={items}
            loading={documents.isFetching}
            sort={sort}
            onSortChange={(s) => {
              setSort(s);
              setPage(0);
            }}
          />

          {data && (
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
      </div>
    </>
  );
}
