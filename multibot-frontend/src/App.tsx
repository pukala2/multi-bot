import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/pages/DashboardPage';
import { DocumentsPage } from '@/pages/DocumentsPage';
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
            <Route path="/documents" element={<DocumentsPage />} />
            <Route
              path="/chat"
              element={
                <PlaceholderPage
                  title="Testowy czat"
                  phaseLabel="Faza F4"
                  description="Test bota ze streamingiem SSE, panel źródeł, formatowanie Markdown. W przygotowaniu."
                />
              }
            />
            <Route
              path="/sessions"
              element={
                <PlaceholderPage
                  title="Sesje"
                  phaseLabel="Faza F5"
                  description="Historia rozmów użytkowników, filtrowanie po dacie, podgląd całych konwersacji. W przygotowaniu."
                />
              }
            />
            <Route
              path="/settings"
              element={
                <PlaceholderPage
                  title="Ustawienia"
                  phaseLabel="Po MVP"
                  description="Dostosuj sposób działania bota: jak rygorystycznie szuka odpowiedzi, ile źródeł bierze pod uwagę, jaki styl odpowiedzi preferuje."
                />
              }
            />
            <Route
              path="/analytics"
              element={
                <PlaceholderPage
                  title="Analityka"
                  phaseLabel="Po MVP"
                  description="Metryki użycia, najczęstsze pytania, koszty tokenów w czasie."
                />
              }
            />
            <Route
              path="*"
              element={
                <PlaceholderPage
                  title="404"
                  phaseLabel="Nie znaleziono"
                  description="Tej strony nie ma. Wróć do panelu albo sprawdź pasek adresu."
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
