// Cloudflare Turnstile (anti-bot). Wlaczane przez backend feature flag.
// W dev backend zwraca enabled:false -> getTurnstileToken zwraca undefined,
// session start idzie bez tokena. Na prod (enabled:true) ladujemy skrypt CF
// i wykonujemy niewidoczna weryfikacje.

interface TurnstileConfig {
  enabled: boolean;
  siteKey: string;
}

declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: Record<string, unknown>) => string;
      execute: (widgetId: string) => void;
      remove: (widgetId: string) => void;
    };
  }
}

let scriptLoaded = false;

function loadScript(): Promise<void> {
  if (scriptLoaded && window.turnstile) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
    s.async = true;
    s.defer = true;
    s.onload = () => { scriptLoaded = true; resolve(); };
    s.onerror = () => reject(new Error('Turnstile script load failed'));
    document.head.appendChild(s);
  });
}

export async function getTurnstileToken(apiUrl: string): Promise<string | undefined> {
  let config: TurnstileConfig;
  try {
    config = await fetch(`${apiUrl}/api/widget/turnstile`).then((r) => r.json());
  } catch {
    return undefined;   // brak configu -> nie blokuj (backend i tak zdecyduje)
  }

  if (!config.enabled || !config.siteKey) return undefined;

  await loadScript();
  if (!window.turnstile) return undefined;

  return new Promise<string | undefined>((resolve) => {
    const container = document.createElement('div');
    container.style.display = 'none';
    document.body.appendChild(container);

    const widgetId = window.turnstile!.render(container, {
      sitekey: config.siteKey,
      callback: (token: string) => {
        resolve(token);
        cleanup();
      },
      'error-callback': () => {
        resolve(undefined);
        cleanup();
      },
    });

    function cleanup() {
      try {
        window.turnstile?.remove(widgetId);
      } catch { /* ignore */ }
      container.remove();
    }

    // timeout zabezpieczajacy
    setTimeout(() => resolve(undefined), 10_000);
  });
}
