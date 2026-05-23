import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<DashboardPage />} />
            <Route
              path="/documents"
              element={
                <PlaceholderPage
                  title="Documents"
                  phaseLabel="Faza F3"
                  description="Tabela dokumentów, drag&drop upload, status polling, delete confirmation. W przygotowaniu."
                />
              }
            />
            <Route
              path="/chat"
              element={
                <PlaceholderPage
                  title="Chat Playground"
                  phaseLabel="Faza F4"
                  description="Test bota z SSE streamingiem, sources panel, markdown rendering. W przygotowaniu."
                />
              }
            />
            <Route
              path="/sessions"
              element={
                <PlaceholderPage
                  title="Sessions"
                  phaseLabel="Faza F5"
                  description="Historia sesji userów, filtrowanie po dacie, podgląd całych rozmów. W przygotowaniu."
                />
              }
            />
            <Route
              path="/settings"
              element={
                <PlaceholderPage
                  title="Settings"
                  phaseLabel="Faza 2"
                  description="Konfiguracja bota: min-score, top-k, temperature, system prompt. Po MVP."
                />
              }
            />
            <Route
              path="/analytics"
              element={
                <PlaceholderPage
                  title="Analytics"
                  phaseLabel="Faza 2"
                  description="Metryki użycia, top pytania, kosztowność. Po MVP."
                />
              }
            />
            <Route
              path="*"
              element={
                <PlaceholderPage
                  title="404"
                  phaseLabel="Not Found"
                  description="Tej strony nie ma. Wróć do Dashboard albo sprawdź pasek adresu."
                />
              }
            />
          </Route>
        </Routes>
      </BrowserRouter>
      <Toaster
        theme="dark"
        position="bottom-right"
        toastOptions={{
          style: {
            background: 'var(--color-bg-panel)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
          },
        }}
      />
    </QueryClientProvider>
  );
}
