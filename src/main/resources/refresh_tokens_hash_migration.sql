-- Run once against an existing PostgreSQL database.
-- This converts existing plaintext refresh tokens to SHA-256 hashes.
-- After this migration, the database no longer contains usable raw refresh tokens.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

UPDATE refresh_tokens
SET token_hash = encode(digest(token, 'sha256'), 'hex')
WHERE token_hash IS NULL
  AND token IS NOT NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

ALTER TABLE refresh_tokens
    DROP COLUMN IF EXISTS token;
