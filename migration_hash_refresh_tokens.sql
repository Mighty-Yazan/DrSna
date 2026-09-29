-- ============================================================
-- Migration: Hash Refresh Tokens
-- Date: 2026-09-29
-- ============================================================
--
-- WHY:
--   The old 'token' column stored raw refresh-token values in
--   plaintext. Anyone with read access to the DB could extract
--   a token and use it to impersonate any user.
--
--   The new 'token_hash' column stores only a SHA-256 hex digest.
--   The raw token only ever lives in the browser cookie; it is
--   never written to disk.
--
-- HOW TO RUN:
--   Execute this script once against your database BEFORE
--   starting the updated application.
--
-- ============================================================

-- Step 1: Invalidate all existing sessions.
--
-- WHY: Old rows contain plaintext tokens. There is no way to
-- hash them retroactively (we don't have the raw values anymore
-- once the old app code is gone). Deleting them forces every
-- user to log in again, which is the correct and safe behavior
-- after a security upgrade.
DELETE FROM refresh_tokens;

-- Step 2: Add the new hashed column.
--
-- WHY: We store a 64-char hex string (SHA-256 = 32 bytes = 64 hex chars).
-- NOT NULL + UNIQUE mirrors the old constraint, but on the hash column.
ALTER TABLE refresh_tokens
    ADD COLUMN token_hash VARCHAR(64) NOT NULL DEFAULT '' UNIQUE;

-- Step 3: Drop the old plaintext column.
--
-- WHY: Keeping it would be misleading and wasteful. Once we hash
-- on write and look up by hash, the old column is dead code.
ALTER TABLE refresh_tokens
    DROP COLUMN token;

-- Step 4: Remove the temporary default we used to satisfy NOT NULL during ADD.
ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash DROP DEFAULT;
