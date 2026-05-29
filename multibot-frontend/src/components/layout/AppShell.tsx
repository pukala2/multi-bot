import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { useAuth } from '@/hooks/useAuth';

export function AppShell() {
  const auth = useAuth();
  // Remount treści strony przy zmianie aktywnego bota — czyści stan lokalny zależny od bota
  // (np. wybrana wersja w BotPage), żeby nie wskazywał danych poprzedniego bota.
  const botKey = auth.data?.activeBot?.id ?? 'no-bot';

  return (
    <div className="grid grid-cols-[240px_1fr] min-h-screen">
      <Sidebar />
      <main className="px-8 py-6 max-w-[1400px] w-full overflow-x-auto">
        <Outlet key={botKey} />
      </main>
    </div>
  );
}
