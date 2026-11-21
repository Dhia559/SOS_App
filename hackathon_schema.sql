begin;

-- =====================================================
-- Hackathon demo app - full schema (PostgreSQL), v2
-- Runs as a single transaction: all or nothing.
-- =====================================================

BEGIN;

-- ---------- Lookup tables ----------

CREATE TABLE roles (
  id    SERIAL PRIMARY KEY,
  name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE clubs (
  id    SERIAL PRIMARY KEY,
  name  VARCHAR(100) NOT NULL UNIQUE
);

-- Reserved "ALL" rows used for targeting (id 0 = everyone).
-- Must be inserted before anything that references them.
-- (The SERIAL sequence starts at 1, so real rows never collide with 0.)

INSERT INTO roles (id, name) VALUES (0, 'ALL');
INSERT INTO clubs (id, name) VALUES (0, 'ALL');


-- ---------- Users ----------

CREATE TABLE users (
  id            SERIAL PRIMARY KEY,
  first_name    VARCHAR(50) NOT NULL,
  last_name     VARCHAR(50) NOT NULL,
  email         VARCHAR(255) NOT NULL,
  password_hash TEXT NOT NULL,
  birthday      DATE,
  sex           CHAR(1) CHECK (sex IN ('M','F')),
  role_id       INT REFERENCES roles(id),
  club_id       INT REFERENCES clubs(id),
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,

  -- Real users can't be assigned to the fake "ALL" role/club.
  CONSTRAINT chk_user_real_role
    CHECK (role_id IS NULL OR role_id <> 0),

  CONSTRAINT chk_user_real_club
    CHECK (club_id IS NULL OR club_id <> 0)
);

-- Case-insensitive uniqueness: Alice@x.com and alice@x.com are the same account.
CREATE UNIQUE INDEX uq_users_email_lower
  ON users (LOWER(email));


-- ---------- Posts, likes, comments ----------

CREATE TABLE posts (
  id               SERIAL PRIMARY KEY,
  user_id          INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content          TEXT NOT NULL,
  visible_role_id  INT NOT NULL DEFAULT 0 REFERENCES roles(id),
  visible_club_id  INT NOT NULL DEFAULT 0 REFERENCES clubs(id),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE device_tokens (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_posts_user
  ON posts(user_id);

CREATE INDEX idx_posts_visibility
  ON posts(visible_role_id, visible_club_id);

CREATE INDEX idx_posts_created_at
  ON posts(created_at DESC);


CREATE TABLE likes (
  user_id     INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id     INT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  PRIMARY KEY (user_id, post_id)
);

-- The PK is (user_id, post_id), so counting likes per post needs its own index.
CREATE INDEX idx_likes_post
  ON likes(post_id);


CREATE TABLE comments (
  id          SERIAL PRIMARY KEY,
  post_id     INT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  user_id     INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content     TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post
  ON comments(post_id, created_at);


-- ---------- Friendships ----------

-- IDs are normalized: user1_id < user2_id.
-- requester_id records who initiated the request.

CREATE TABLE friendships (
  id            SERIAL PRIMARY KEY,
  user1_id      INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user2_id      INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  requester_id  INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status        VARCHAR(10) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending','accepted','blocked')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (user1_id < user2_id),
  CHECK (requester_id IN (user1_id, user2_id)),
  UNIQUE (user1_id, user2_id)
);

CREATE INDEX idx_friendships_user1_status
  ON friendships(user1_id, status);

CREATE INDEX idx_friendships_user2_status
  ON friendships(user2_id, status);

CREATE INDEX idx_friendships_requester_status
  ON friendships(requester_id, status);


-- ---------- Chat (1-to-1) ----------

-- IDs are normalized: user1_id < user2_id.

CREATE TABLE conversations (
  id          SERIAL PRIMARY KEY,
  user1_id    INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  user2_id    INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (user1_id < user2_id),
  UNIQUE (user1_id, user2_id)
);

-- UNIQUE(user1_id, user2_id) already serves lookups by user1_id;
-- this one serves "all conversations of user X" when X is user2_id.
CREATE INDEX idx_conversations_user2
  ON conversations(user2_id);


CREATE TABLE messages (
  id               SERIAL PRIMARY KEY,
  conversation_id  INT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id        INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content          TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_read          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_messages_conversation
  ON messages(conversation_id, created_at);

CREATE INDEX idx_messages_sender
  ON messages(sender_id);

-- Fast "unread messages" counts.
CREATE INDEX idx_messages_unread
  ON messages(conversation_id) WHERE is_read = FALSE;


-- ---------- Notifications ----------

-- Each row represents a notification for one recipient.
-- seen is tracked per recipient, not per broadcast.
-- created_by is NULL for system-generated notifications (e.g. from the
-- event worker) and is kept (set to NULL) if the creating user is deleted.

CREATE TABLE notifications (
  id               SERIAL PRIMARY KEY,
  title            VARCHAR(150) NOT NULL,
  content          TEXT NOT NULL,
  created_by       INT REFERENCES users(id) ON DELETE SET NULL,
  recipient_id     INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  seen             BOOLEAN NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient
  ON notifications(recipient_id, created_at DESC);

-- Fast unread badge count.
CREATE INDEX idx_notifications_unseen
  ON notifications(recipient_id) WHERE seen = FALSE;


-- ---------- Event queue / Outbox ----------

-- Events are persisted in the same database as the feed.
-- The feed service should insert the post and its event
-- in the same transaction, so the event cannot be lost
-- if the notification service is unavailable.
--
-- Lifecycle: pending -> processing -> processed
--                          |-> pending (retry, with backoff)
--                          |-> failed  (after too many attempts)
-- locked_at lets a "reaper" rescue events stuck in 'processing'
-- because a worker crashed.

CREATE TABLE events (
  id            BIGSERIAL PRIMARY KEY,
  event_type    VARCHAR(100) NOT NULL,
  payload       JSONB NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'processing', 'processed', 'failed')),
  attempts      INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  locked_at     TIMESTAMPTZ,
  processed_at  TIMESTAMPTZ,
  next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_error    TEXT
);

-- Partial indexes: only the rows workers actually scan.
CREATE INDEX idx_events_pending
  ON events(next_retry_at, created_at) WHERE status = 'pending';

CREATE INDEX idx_events_processing
  ON events(locked_at) WHERE status = 'processing';


commit;