# Multibot Frontend

Admin panel dla Multibota — zarządzanie dokumentami, czat playground, sesje, ustawienia.

## Stack

- **Vite 8** + **React 19** + **TypeScript 5.7**
- **Tailwind CSS v4** (CSS-first config przez `@theme`)
- **shadcn/ui-style** komponenty (ręcznie, pod nasz design system)
- **TanStack Query** v5 — fetching + caching + SSE
- **React Router** v7 — routing
- **openapi-typescript** + **openapi-fetch** — type-safe API client z OpenAPI spec backendu
- **sonner** — toasty
- **lucide-react** — ikony

## Design tokens

Styl: **Linear-inspired dark theme**. Kolory zdefiniowane w `src/index.css` jako Tailwind v4 `@theme`:

| Token | Wartość | Zastosowanie |
|-------|---------|--------------|
| `bg-base` | `#08090A` | tło aplikacji |
| `bg-panel` | `#111213` | tła paneli/cards |
| `bg-elevated` | `#18191B` | hover, badge bg |
| `accent` | `#7B5BFF` | primary buttons, links, focus |
| `text` | `#E6E6E6` | główny tekst |
| `text-muted` | `#A0A0A8` | drugorzędny |
| `text-dim` | `#6E6E76` | meta info |

Statusy: `success` (zielony), `warning` (żółty), `error` (czerwony), `info` (niebieski).

Fonty: **Inter** (UI) + **JetBrains Mono** (UUIDy, kod) — z Google Fonts.

## Uruchomienie

```bash
# Pierwsze uruchomienie (po klonowaniu):
npm install

# Skopiuj env i ewentualnie zmień:
cp .env.example .env.local

# Dev server (port 5173, proxy na backend :8080):
npm run dev

# Otwórz http://localhost:5173
```

Backend musi działać na `localhost:8080` (Vite proxy przekierowuje `/api/*` i `/actuator/*`).

## Generowanie typów z OpenAPI

Po zmianie `docs/api/openapi.yaml` w backendzie:

```bash
npm run gen:api
```

Wypluwa `src/api/schema.ts` z pełnymi typami endpointów + DTO.

## Struktura

```
src/
├── api/                # OpenAPI schema + fetch client
├── components/
│   ├── ui/             # Button, Card, Input, Badge - design system primitives
│   ├── layout/         # AppShell, Sidebar, Topbar
│   └── domain/         # DocumentTable, ChatMessage (dochodzą w F3+)
├── hooks/              # useDocuments, useUploadDocument, useChatStream (F2+)
├── lib/                # cn(), format() helpers
├── pages/              # DashboardPage, DocumentsPage, ChatPage, ...
├── App.tsx             # router + providers
├── main.tsx
└── index.css           # Tailwind + design tokens
```

## Build production

```bash
npm run build
# Output: dist/ (statyczne pliki do nginx/CDN)
```

## Status fazy

- ✅ **F1** — Foundation (Vite + React + TS + Tailwind + shadcn-style + routing + app shell)
- ⏳ **F2** — OpenAPI codegen (gen:api script gotowy, pierwsze typed hooks)
- ⏳ **F3** — Documents module
- ⏳ **F4** — Chat playground (SSE)
- ⏳ **F5** — Sessions history
- ⏳ **F6** — Dashboard + polish
