-- =====================================================
-- Migration: photo + event on posts.
-- For databases created before this change (hackathon_schema.sql already
-- contains it). Safe to run several times.
--   psql -h localhost -U postgres -d postgres -f sos-backend/db/03_post_media.sql
-- =====================================================

BEGIN;

ALTER TABLE posts ADD COLUMN IF NOT EXISTS event_title     VARCHAR(150);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS event_starts_at TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS event_place     VARCHAR(150);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_post_event') THEN
    ALTER TABLE posts ADD CONSTRAINT chk_post_event
      CHECK ((event_title IS NULL) = (event_starts_at IS NULL));
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS post_images (
  post_id       INT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE,
  content_type  VARCHAR(50) NOT NULL,
  data          BYTEA NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_posts_event_starts_at
  ON posts(event_starts_at) WHERE event_starts_at IS NOT NULL;

COMMIT;
