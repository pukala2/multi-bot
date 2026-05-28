# Deploy Multibota na serwer (Hetzner + DuckDNS)

Strategia: build na serwerze z kodu (git clone). Serwer potrzebuje tylko **Docker + git**.
Maven i Node biegną wewnątrz Dockera (multi-stage). HTTPS automatyczny przez Caddy.

Wymagania wstępne:
- Serwer Ubuntu z dostępem SSH (np. Hetzner CPX22)
- Domena wskazująca na IP serwera (np. DuckDNS)
- Klucze API: OpenAI + Anthropic
- Repo na GitHubie

---

## 1. Wejdź na serwer

```bash
ssh root@TWOJE_IP
```

## 2. Swap 2 GB (insurance na build przy 4 GB RAM)

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h   # sprawdz ze swap jest
```

## 3. Zainstaluj Docker + git

```bash
apt update
apt install -y git ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
docker --version   # sprawdz
```

## 4. Sklonuj repo

```bash
cd /opt
git clone https://github.com/pukala2/multi-bot.git multibot
cd multibot
```

> Repo prywatne? Przy `git clone` GitHub poprosi o login + Personal Access Token
> (Settings → Developer settings → Tokens). Albo dodaj deploy key.

## 5. Skonfiguruj sekrety

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```

Uzupełnij **prawdziwymi** wartościami:
- `DOMAIN` — twoja domena DuckDNS (np. `multibot-test.duckdns.org`)
- `DB_PASSWORD` — wymyśl mocne hasło
- `OPENAI_API_KEY`, `ANTHROPIC_API_KEY` — z paneli
- `MULTIBOT_SUPERADMIN_PASSWORD`, `MULTIBOT_DEMO_OWNER_PASSWORD` — mocne hasła
- `MULTIBOT_TURNSTILE_ENABLED=false` — **zostaw false na teraz**

Zapisz: Ctrl+O, Enter, Ctrl+X.

## 6. Sprawdź że DNS wskazuje na serwer

```bash
ping -c2 TWOJA_DOMENA   # powinno pokazac IP serwera
```

Jeśli nie — popraw IP w DuckDNS i poczekaj chwilę. **Caddy nie dostanie certu HTTPS
jeśli domena nie wskazuje na serwer.**

## 7. Pierwszy build + start (BEZ Turnstile)

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

Pierwszy build ~5-10 min (ściąga zależności Maven + npm). Potem:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env ps
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env logs -f backend
```

Czekaj aż w logach: `Started MultibotApplication`. Ctrl+C wychodzi z logów (kontener działa dalej).

## 8. Test

```
https://TWOJA_DOMENA            → panel logowania (HTTPS, zielona kłódka)
https://TWOJA_DOMENA/actuator/health → {"status":"UP"}
```

Zaloguj się super adminem (z .env). HTTPS Caddy ogarnia sam przy pierwszym wejściu
(może chwilę potrwać zanim wyrobi cert).

## 9. Tenant znajomego (przez SQL — Faza D UI jeszcze nie ma)

```bash
docker exec -it multibot-postgres psql -U multibot -d multibot
```

W psql (podstaw dane znajomego):
```sql
-- nowy tenant
INSERT INTO tenants (id, name, slug, plan, status, created_at, updated_at)
VALUES (gen_random_uuid(), 'Firma Znajomego', 'znajomy', 'FREE', 'ACTIVE', now(), now());

-- zapamietaj id:
SELECT id FROM tenants WHERE slug = 'znajomy';

-- wersja bota (aktywna)
-- ... (na razie najprościej: użyj Default Tenant do testu, patrz niżej)
\q
```

> **Prościej na pierwszy test:** użyj istniejącego Default Tenant. Zaloguj się jako
> `owner@demo.local`, wgraj dokumenty przez panel, ustaw allowed_origins na domenę
> znajomego, skopiuj jego embed z zakładki Widget.

## 10. Ogranicz widget do domeny znajomego

```bash
docker exec -it multibot-postgres psql -U multibot -d multibot -c \
  "UPDATE widget_keys SET allowed_origins = ARRAY['https://strona-znajomego.pl'] WHERE tenant_id = '00000000-0000-7000-8000-000000000001';"
```

## 11. Embed u znajomego

Znajomy wkleja na swojej stronie (klucz z zakładki Widget w panelu):
```html
<script
  src="https://TWOJA_DOMENA/widget/embed.js"
  data-multibot-widget
  data-key="pk_widget_live_..."
  data-api-url="https://TWOJA_DOMENA"
></script>
```

Test: otwiera stronę → dymek → pisze → bot odpowiada. W panelu rośnie licznik.

## 12. Turnstile — OSTATNI krok (gdy reszta działa)

```
1. Cloudflare → Turnstile → Add site → domena znajomego (+ www)
2. Dostajesz SITE_KEY + SECRET_KEY
3. nano deploy/.env:
     MULTIBOT_TURNSTILE_ENABLED=true
     MULTIBOT_TURNSTILE_SITE_KEY=...
     MULTIBOT_TURNSTILE_SECRET_KEY=...
4. Restart backendu:
     docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d backend
5. Test: widget znajomego nadal startuje rozmowe?
     TAK → zostaje.  NIE → ENABLED=false, restart, debug.
```

---

## Przydatne komendy

```bash
# alias zeby nie pisac dlugiej komendy:
alias dc='docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env'

dc ps                 # status
dc logs -f backend    # logi backendu
dc logs -f web        # logi Caddy (HTTPS)
dc restart backend    # restart samego backendu
dc down               # zatrzymaj wszystko (dane zostaja w volumes)
dc up -d --build      # przebuduj i wstaw

# Nowy deploy po zmianach w kodzie:
git pull
dc up -d --build
```

## Backup bazy (ręczny)

```bash
docker exec multibot-postgres pg_dump -U multibot multibot > backup_$(date +%F).sql
```

## Troubleshooting

- **Brak HTTPS / cert error** → DNS nie wskazuje na serwer, albo port 80/443 zablokowany.
  Sprawdz `ping domena` + `dc logs web`.
- **Backend nie wstaje** → `dc logs backend`. Czesto: zly klucz API albo DB_PASSWORD.
- **Widget 403** → sprawdz allowed_origins dla klucza (musi byc domena znajomego z https://).
- **Build OOM** → upewnij sie ze swap jest (`free -h`), krok 2.
