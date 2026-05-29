import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { plPlural } from '@/lib/format';

interface PaginationFooterProps {
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  /** Formy odmiany PL: [lp, lm 2–4, dopełniacz lm]. Domyślnie dokumenty. */
  itemForms?: [string, string, string];
}

const PAGE_SIZES = [10, 20, 50];

export function PaginationFooter({
  page,
  size,
  totalPages,
  totalElements,
  onPageChange,
  onSizeChange,
  itemForms = ['dokument', 'dokumenty', 'dokumentów'],
}: PaginationFooterProps) {
  return (
    <div className="px-5 py-3 border-t border-border flex items-center justify-between text-[12.5px] text-text-muted">
      <div>
        Łącznie <span className="text-text font-medium">{totalElements}</span>{' '}
        {plPlural(totalElements, itemForms)}
      </div>

      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <span>Na stronie:</span>
          <select
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
            className="bg-bg-panel border border-border rounded px-2 py-1 text-[12px] text-text focus:outline-none focus:border-accent"
          >
            {PAGE_SIZES.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            aria-label="Poprzednia strona"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="px-2">
            <span className="text-text">{page + 1}</span> / {Math.max(1, totalPages)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            aria-label="Następna strona"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
