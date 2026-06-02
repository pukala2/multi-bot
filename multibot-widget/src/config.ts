export interface WidgetConfig {
  apiUrl: string;
  publicKey: string | null;
  accentColor: string;
  iconColor: string;           // kolor launchera/avatara (z serwera iconColor ?? accentColor)
  iconUrl: string | null;      // absolutny URL własnej ikonki bota (null = ikona domyślna)
  position: 'bottom-right' | 'bottom-left';
  greeting: string;
  title: string;
  placeholder: string;
}

export interface BootConfig {
  apiUrl: string;
  publicKey: string | null;
  overrides: Partial<WidgetConfig>;
}

const DEFAULTS: WidgetConfig = {
  apiUrl: 'http://localhost:8080',
  publicKey: null,
  accentColor: '#7B5BFF',
  iconColor: '#7B5BFF',
  iconUrl: null,
  position: 'bottom-right',
  greeting: 'Cześć! Jak mogę pomóc?',
  title: 'Asystent',
  placeholder: 'Napisz wiadomość…',
};

export function readBootConfig(): BootConfig {
  const script = (document.currentScript as HTMLScriptElement | null)
    ?? document.querySelector('script[data-multibot-widget]') as HTMLScriptElement | null;

  const apiUrl = script?.dataset.apiUrl ?? DEFAULTS.apiUrl;
  const publicKey = script?.dataset.key ?? null;

  const overrides: Partial<WidgetConfig> = {};
  if (script?.dataset.color) overrides.accentColor = script.dataset.color;
  if (script?.dataset.position) overrides.position = script.dataset.position as WidgetConfig['position'];
  if (script?.dataset.greeting) overrides.greeting = script.dataset.greeting;
  if (script?.dataset.title) overrides.title = script.dataset.title;
  if (script?.dataset.placeholder) overrides.placeholder = script.dataset.placeholder;

  return { apiUrl, publicKey, overrides };
}

export async function resolveConfig(boot: BootConfig): Promise<WidgetConfig> {
  let serverConfig: Partial<WidgetConfig> = {};

  if (boot.publicKey) {
    try {
      const response = await fetch(
        `${boot.apiUrl}/api/widget/config?key=${encodeURIComponent(boot.publicKey)}`
      );
      if (response.ok) {
        const data = await response.json() as {
          accentColor?: string;
          iconColor?: string | null;
          iconUrl?: string | null;
          position?: WidgetConfig['position'];
          title?: string;
          greeting?: string;
          placeholder?: string;
        };
        serverConfig = {
          accentColor: data.accentColor,
          iconColor: data.iconColor ?? undefined,
          // iconUrl z serwera jest względny (/api/widget/icon/...) — robimy absolutny do apiUrl
          iconUrl: data.iconUrl ? `${boot.apiUrl}${data.iconUrl}` : null,
          position: data.position,
          title: data.title,
          greeting: data.greeting,
          placeholder: data.placeholder,
        };
      }
    } catch {
      // fall back to defaults + overrides
    }
  }

  const accentColor = boot.overrides.accentColor ?? serverConfig.accentColor ?? DEFAULTS.accentColor;
  return {
    apiUrl: boot.apiUrl,
    publicKey: boot.publicKey,
    accentColor,
    iconColor: serverConfig.iconColor ?? accentColor,   // brak iconColor => kolor akcentu
    iconUrl: serverConfig.iconUrl ?? null,
    position: boot.overrides.position ?? serverConfig.position ?? DEFAULTS.position,
    greeting: boot.overrides.greeting ?? serverConfig.greeting ?? DEFAULTS.greeting,
    title: boot.overrides.title ?? serverConfig.title ?? DEFAULTS.title,
    placeholder: boot.overrides.placeholder ?? serverConfig.placeholder ?? DEFAULTS.placeholder,
  };
}
