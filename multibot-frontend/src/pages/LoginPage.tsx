import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { Card, CardBody, CardHeader, CardTitle, CardSubtitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth, useLogin } from '@/hooks/useAuth';

export function LoginPage() {
  const auth = useAuth();
  const login = useLogin();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (auth.data) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    try {
      await login.mutateAsync({ email, password });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Nieznany błąd');
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-bg">
      <Card className="w-full max-w-[400px]">
        <CardHeader>
          <div>
            <CardTitle>Multibot</CardTitle>
            <CardSubtitle>Zaloguj się do panelu administracyjnego</CardSubtitle>
          </div>
        </CardHeader>
        <CardBody>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <label htmlFor="email" className="text-[12px] text-text-muted">
                Email
              </label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ty@firma.pl"
                autoComplete="email"
                required
                autoFocus
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="password" className="text-[12px] text-text-muted">
                Hasło
              </label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                required
              />
            </div>

            {error && (
              <div className="text-[12px] text-error bg-error/10 border border-error/20 rounded-md px-3 py-2">
                {error}
              </div>
            )}

            <Button
              type="submit"
              size="lg"
              className="w-full"
              disabled={login.isPending || !email || !password}
            >
              {login.isPending ? 'Logowanie…' : 'Zaloguj się'}
            </Button>
          </form>
        </CardBody>
      </Card>
    </div>
  );
}
