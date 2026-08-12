-- O indice por expressao protege tanto a API atual quanto dados inseridos
-- diretamente no SQLite. Se houver duplicatas antigas, a migration para aqui.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_normalized
ON users(lower(trim(email)));
--;;
UPDATE users SET email = lower(trim(email));
--;;
ALTER TABLE users
ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;
