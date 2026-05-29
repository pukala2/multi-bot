export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(i === 0 ? 0 : 1)} ${sizes[i]}`;
}

export function shortUuid(uuid: string, head = 8, tail = 4): string {
  if (uuid.length <= head + tail + 1) return uuid;
  return `${uuid.slice(0, head)}…${uuid.slice(-tail)}`;
}

export function timeAgo(isoDate: string): string {
  const date = new Date(isoDate);
  const diffMs = Date.now() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60) return 'just now';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH} h ago`;
  const diffD = Math.floor(diffH / 24);
  if (diffD < 7) return `${diffD} d ago`;
  return date.toLocaleDateString();
}

export function formatScore(score: number): string {
  return score.toFixed(3);
}

/**
 * Polska odmiana liczebnika. Zwraca samą formę rzeczownika (bez liczby).
 * formy = [mianownik lp, mianownik lm 2–4, dopełniacz lm].
 * Np. plPlural(4, ['rozmowa','rozmowy','rozmów']) === 'rozmowy'.
 */
export function plPlural(n: number, forms: readonly [string, string, string]): string {
  const abs = Math.abs(n);
  const mod10 = abs % 10;
  const mod100 = abs % 100;
  if (abs === 1) return forms[0];
  if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return forms[1];
  return forms[2];
}

export function formatDateTime(isoDate: string): string {
  return new Date(isoDate).toLocaleString('pl-PL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatTime(isoDate: string): string {
  return new Date(isoDate).toLocaleTimeString('pl-PL', {
    hour: '2-digit',
    minute: '2-digit',
  });
}
