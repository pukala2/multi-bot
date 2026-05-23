import { NavLink } from 'react-router-dom';
import { LayoutDashboard, FileText, MessageSquare, Inbox, Settings, BarChart3 } from 'lucide-react';
import { cn } from '@/lib/cn';

const NAV_GROUPS = [
  {
    label: 'Główne',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Panel' },
      { to: '/documents', icon: FileText, label: 'Dokumenty' },
      { to: '/chat', icon: MessageSquare, label: 'Testowy czat' },
      { to: '/sessions', icon: Inbox, label: 'Sesje' },
    ],
  },
  {
    label: 'System',
    items: [
      { to: '/settings', icon: Settings, label: 'Ustawienia' },
      { to: '/analytics', icon: BarChart3, label: 'Analityka' },
    ],
  },
];

export function Sidebar() {
  return (
    <aside className="w-60 bg-bg-panel border-r border-border flex flex-col p-3">
      <div className="flex items-center gap-2.5 px-3 pt-2 pb-5">
        <div className="size-7 bg-gradient-to-br from-accent to-info rounded-lg flex items-center justify-center text-white font-bold text-sm">
          M
        </div>
        <span className="font-semibold text-[15px]">Multibot</span>
      </div>

      {NAV_GROUPS.map((group) => (
        <div key={group.label}>
          <div className="px-3 pt-4 pb-2 text-[11px] uppercase tracking-wider text-text-dim font-semibold">
            {group.label}
          </div>
          {group.items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                cn(
                  'group relative flex items-center gap-2.5 px-3 py-2 rounded-md text-[13.5px] transition-colors',
                  isActive
                    ? 'bg-bg-elevated text-text'
                    : 'text-text-muted hover:bg-bg-elevated hover:text-text'
                )
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <span className="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-4 bg-accent rounded-r" />
                  )}
                  <item.icon className="size-4 opacity-70" />
                  {item.label}
                </>
              )}
            </NavLink>
          ))}
        </div>
      ))}

      <div className="mt-auto pt-4 px-3 text-[11px] text-text-dim mono border-t border-border">
        tenant 0000…0001
        <br />
        v0.1.0 · main
      </div>
    </aside>
  );
}
