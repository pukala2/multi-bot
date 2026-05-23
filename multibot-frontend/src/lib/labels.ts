export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING: 'Oczekuje',
  PROCESSING: 'Przetwarzanie',
  READY: 'Gotowe',
  FAILED: 'Błąd',
};

export function documentStatusLabel(status: string): string {
  return DOCUMENT_STATUS_LABELS[status as DocumentStatus] ?? status;
}

export type DocumentStatusVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral';

const DOCUMENT_STATUS_VARIANTS: Record<DocumentStatus, DocumentStatusVariant> = {
  PENDING: 'info',
  PROCESSING: 'warning',
  READY: 'success',
  FAILED: 'error',
};

export function documentStatusVariant(status: string): DocumentStatusVariant {
  return DOCUMENT_STATUS_VARIANTS[status as DocumentStatus] ?? 'neutral';
}
