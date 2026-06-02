-- Własna ikonka widgetu + osobny kolor ikonki/launchera (niezależny od accent_color).
-- Obrazek trzymany jako BYTEA bezpośrednio w wierszu (cap 512 KB) — proste publiczne serwowanie
-- po kluczu widgetu, bez zależności od modułu file / S3 i bez TenantContext dla żądania anonimowego.

ALTER TABLE widget_settings ADD COLUMN icon_color      VARCHAR(20);   -- NULL = użyj accent_color
ALTER TABLE widget_settings ADD COLUMN icon_bytes      BYTEA;         -- NULL = ikona domyślna
ALTER TABLE widget_settings ADD COLUMN icon_mime       VARCHAR(64);   -- image/png | image/jpeg | image/webp
ALTER TABLE widget_settings ADD COLUMN icon_updated_at TIMESTAMPTZ;   -- do cache-bustingu URL-a (?v=)
