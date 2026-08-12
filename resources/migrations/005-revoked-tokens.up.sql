CREATE TABLE revoked_tokens (
  jti TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL
);
--;;
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
