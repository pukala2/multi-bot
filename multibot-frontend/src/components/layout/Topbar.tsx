import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';

interface TopbarProps {
  title: string;
  actions?: React.ReactNode;
}

export function Topbar({ title, actions }: TopbarProps) {
  return (
    <div className="flex justify-between items-center mb-8">
      <h1 className="text-[22px] font-semibold tracking-tight">{title}</h1>
      <div className="flex gap-2 items-center">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 size-3.5 text-text-muted pointer-events-none" />
          <Input placeholder="Szukaj…" className="pl-9 w-[280px]" />
        </div>
        {actions}
      </div>
    </div>
  );
}
