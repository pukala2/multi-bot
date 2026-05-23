import type { HTMLAttributes, ThHTMLAttributes, TdHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return <table className={cn('w-full border-collapse', className)} {...props} />;
}

export function TableHeader({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('bg-bg-elevated', className)} {...props} />;
}

export function TableBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={className} {...props} />;
}

export function TableRow({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('border-t border-border hover:bg-bg-elevated/40 transition-colors', className)} {...props} />;
}

export function TableHead({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        'text-left px-5 py-3 text-[11px] uppercase tracking-wider font-semibold text-text-dim',
        className
      )}
      {...props}
    />
  );
}

export function TableCell({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-5 py-3.5 text-[13px]', className)} {...props} />;
}
