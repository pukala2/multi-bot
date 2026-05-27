import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Bot, MessageSquare, Inbox, Settings, BarChart3, Code, LogOut } from 'lucide-react';
import { cn } from '@/lib/cn';
import { useAuth, useLogout } from '@/hooks/useAuth';

const NAV_GROUPS = [
  {
    label: 'Główne',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Panel' },
      { to: '/bot', icon: Bot, label: 'Bot' },
      { to: '/chat', icon: MessageSquare, label: 'Testowy czat' },
      { to: '/sessions', icon: Inbox, label: 'Sesje' },
      { to: '/widget', icon: Code, label: 'Widget' },
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
  const auth = useAuth();
  const logout = useLogout();
  const user = auth.data;

  const roleLabel = user?.role === 'SUPER_ADMIN' ? 'Super admin'
    : user?.role === 'OWNER' ? 'Owner'
    : user?.role === 'ADMIN' ? 'Admin'
    : '';

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

      <div className="mt-auto pt-4 border-t border-border space-y-3">
        {user && (
          <div className="px-3">
            <div className="text-[12.5px] text-text font-medium truncate" title={user.email}>
              {user.email}
            </div>
            <div className="text-[11px] text-text-dim flex items-center justify-between">
              <span>{roleLabel}</span>
              <button
                onClick={() => logout.mutate()}
                disabled={logout.isPending}
                className="inline-flex items-center gap-1 text-text-muted hover:text-text transition-colors"
                title="Wyloguj"
              >
                <LogOut className="size-3" />
                wyloguj
              </button>
            </div>
          </div>
        )}
        <div className="px-3 text-[11px] text-text-dim mono">
          v0.1.0 · main
        </div>
      </div>
    </aside>
  );
}
