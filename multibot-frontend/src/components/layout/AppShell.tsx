import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export function AppShell() {
  return (
    <div className="grid grid-cols-[240px_1fr] min-h-screen">
      <Sidebar />
      <main className="px-8 py-6 max-w-[1400px] w-full overflow-x-auto">
        <Outlet />
      </main>
    </div>
  );
}
