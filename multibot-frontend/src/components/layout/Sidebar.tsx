import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { LayoutDashboard, Bot, MessageSquare, Inbox, Settings, BarChart3, Code, LogOut, CreditCard, Building2, ChevronsUpDown, Check, Plus } from 'lucide-react';
import { cn } from '@/lib/cn';
import { botColor, botColorTint } from '@/lib/botColor';
import { useAuth, useLogout, useSwitchActiveBot, type UserRole } from '@/hooks/useAuth';
import { AddBotDialog } from '@/components/bots/AddBotDialog';

interface NavItem {
  to: string;
  icon: LucideIcon;
  label: string;
  hiddenFor?: UserRole[];
  onlyFor?: UserRole[];
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Główne',
    // Strony zależne od bota (tenant) — super admin nie ma aktywnego bota, więc je chowamy.
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Panel', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/bot', icon: Bot, label: 'Bot', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/chat', icon: MessageSquare, label: 'Testowy czat', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/sessions', icon: Inbox, label: 'Sesje', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/widget', icon: Code, label: 'Widget', hiddenFor: ['SUPER_ADMIN'] },
    ],
  },
  {
    label: 'System',
    items: [
      { to: '/settings', icon: Settings, label: 'Ustawienia', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/billing', icon: CreditCard, label: 'Plan i limity', hiddenFor: ['SUPER_ADMIN'] },
      { to: '/analytics', icon: BarChart3, label: 'Analityka', hiddenFor: ['SUPER_ADMIN'] },
    ],
  },
  {
    label: 'Platforma',
    items: [
      { to: '/super-admin', icon: Building2, label: 'Klienci', onlyFor: ['SUPER_ADMIN'] },
    ],
  },
];

// Przełącznik aktywnego bota. Widoczny TYLKO gdy organizacja ma >= 2 boty —
// przy jednym bocie panel wygląda dokładnie jak przed zmianami (brak tej sekcji).
function BotSwitcher() {
  const auth = useAuth();
  const switchBot = useSwitchActiveBot();
  const [open, setOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);

  const user = auth.data;
  if (!user || user.role === 'SUPER_ADMIN') return null;
  const bots = user.bots ?? [];
  if (bots.length < 2) return null;

  const active = user.activeBot;
  return (
    <div className="px-1 pb-2">
      <div className="px-2 pb-1.5 text-[10px] uppercase tracking-wider text-text-dim font-semibold">
        Aktywny bot
      </div>
      <div className="relative">
        <button
          onClick={() => setOpen((o) => !o)}
          className="flex items-center gap-2.5 w-full px-2.5 py-2 rounded-lg bg-bg-elevated border border-border-strong hover:border-accent transition-colors text-left"
        >
          <span
            className="size-[22px] rounded-md flex items-center justify-center shrink-0"
            style={{ color: botColor(active?.id ?? ''), backgroundColor: botColorTint(active?.id ?? '') }}
          >
            <Bot className="size-3.5" />
          </span>
          <span className="flex-1 min-w-0 text-[13.5px] font-semibold truncate">
            {active?.name ?? 'Wybierz bota'}
          </span>
          <ChevronsUpDown className="size-3.5 text-text-dim shrink-0" />
        </button>
        {open && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
            <div className="absolute top-[calc(100%+6px)] left-0 right-0 z-20 bg-bg-elevated border border-border-strong rounded-lg p-1.5 shadow-xl">
              {bots.map((b) => {
                const isActive = b.id === active?.id;
                return (
                  <button
                    key={b.id}
                    disabled={switchBot.isPending}
                    onClick={() => {
                      setOpen(false);
                      if (!isActive) switchBot.mutate(b.id);
                    }}
                    className={cn(
                      'flex items-center gap-2.5 w-full px-2 py-2 rounded-md text-[13px] transition-colors disabled:opacity-50',
                      isActive ? 'text-text' : 'text-text-muted hover:bg-bg-hover hover:text-text'
                    )}
                  >
                    <span
                      className="size-5 rounded flex items-center justify-center shrink-0"
                      style={{ color: botColor(b.id), backgroundColor: botColorTint(b.id) }}
                    >
                      <Bot className="size-3" />
                    </span>
                    <span className="flex-1 min-w-0 truncate text-left">{b.name}</span>
                    {isActive && <Check className="size-3.5 text-accent shrink-0" />}
                  </button>
                );
              })}
              <div className="h-px bg-border my-1.5" />
              <button
                onClick={() => { setOpen(false); setAddOpen(true); }}
                className="flex items-center gap-2.5 w-full px-2 py-2 rounded-md text-[13px] text-accent hover:bg-bg-hover transition-colors"
              >
                <span className="size-5 rounded flex items-center justify-center shrink-0">
                  <Plus className="size-3.5" />
                </span>
                Dodaj bota
              </button>
            </div>
          </>
        )}
      </div>
      <AddBotDialog open={addOpen} onOpenChange={setAddOpen} />
    </div>
  );
}

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

      <BotSwitcher />

      {NAV_GROUPS.map((group) => {
        const visibleItems = group.items.filter((item) => {
          if (item.hiddenFor && user && item.hiddenFor.includes(user.role)) return false;
          if (item.onlyFor && (!user || !item.onlyFor.includes(user.role))) return false;
          return true;
        });
        if (visibleItems.length === 0) return null;
        return (
        <div key={group.label}>
          <div className="px-3 pt-4 pb-2 text-[11px] uppercase tracking-wider text-text-dim font-semibold">
            {group.label}
          </div>
          {visibleItems.map((item) => (
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
        );
      })}

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
