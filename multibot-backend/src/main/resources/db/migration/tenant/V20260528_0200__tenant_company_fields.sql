-- Opcjonalne dane firmowe klienta (do faktur w przyszlosci).
ALTER TABLE tenants ADD COLUMN company_name VARCHAR(255);
ALTER TABLE tenants ADD COLUMN nip          VARCHAR(20);
