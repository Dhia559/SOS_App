-- =====================================================
-- Hackathon demo app - test data for schema v2
-- Run AFTER hackathon_schema.sql, on empty tables
-- (the only pre-existing rows are the reserved id 0 "ALL"
-- rows in roles and clubs).
--
-- Passwords: every user has the same FAKE hash, not a real bcrypt hash.
-- Replace it with a real hash of your test password if you want to log in
-- (generate one with your backend's hashing function).
-- =====================================================

BEGIN;

-- ---------- Roles & clubs ----------
INSERT INTO roles (id, name) VALUES
  (1, 'President Association'),
  (2, 'Membre Association'),
  (3, 'President Club'),
  (4, 'Membre Club');

INSERT INTO clubs (id, name) VALUES
  (1, 'ISIMM'),
  (2, 'FSM'),
  (3, 'ISSATSO'),
  (4, 'ENIM');

-- ---------- Users (20) ----------
-- Presidents: 2 (Chess), 5 (Robotics), 9 (Music), 13 (Photography)
-- Vice-presidents: 4, 8, 12
-- User 17 is inactive, user 19 has no club.
INSERT INTO users (id, first_name, last_name, email, password_hash, birthday, sex, role_id, club_id, is_active) VALUES
  (1,  'Amine',   'Ben Salah',  'amine.bensalah@example.com',  'FAKE_HASH_replace_me', '2001-03-14', 'M', 1, 1, TRUE),
  (2,  'Sarra',   'Trabelsi',   'sarra.trabelsi@example.com',  'FAKE_HASH_replace_me', '2002-07-21', 'F', 2, 1, TRUE),
  (3,  'Youssef', 'Mansour',    'youssef.mansour@example.com', 'FAKE_HASH_replace_me', '2003-01-09', 'M', 4, 1, TRUE),
  (4,  'Ines',    'Gharbi',     'ines.gharbi@example.com',     'FAKE_HASH_replace_me', '2002-11-30', 'F', 3, 1, TRUE),
  (5,  'Mehdi',   'Jlassi',     'mehdi.jlassi@example.com',    'FAKE_HASH_replace_me', '2001-05-02', 'M', 2, 2, TRUE),
  (6,  'Nour',    'Hamdi',      'nour.hamdi@example.com',      'FAKE_HASH_replace_me', '2003-09-17', 'F', 4, 2, TRUE),
  (7,  'Karim',   'Bouzid',     'karim.bouzid@example.com',    'FAKE_HASH_replace_me', '2002-02-25', 'M', 4, 2, TRUE),
  (8,  'Yasmine', 'Chaabane',   'yasmine.chaabane@example.com','FAKE_HASH_replace_me', '2002-04-12', 'F', 3, 2, TRUE),
  (9,  'Omar',    'Ferchichi',  'omar.ferchichi@example.com',  'FAKE_HASH_replace_me', '2001-12-05', 'M', 2, 3, TRUE),
  (10, 'Lina',    'Maatoug',    'lina.maatoug@example.com',    'FAKE_HASH_replace_me', '2003-06-28', 'F', 4, 3, TRUE),
  (11, 'Hedi',    'Zouari',     'hedi.zouari@example.com',     'FAKE_HASH_replace_me', '2002-08-19', 'M', 4, 3, TRUE),
  (12, 'Rim',     'Dridi',      'rim.dridi@example.com',       'FAKE_HASH_replace_me', '2002-10-03', 'F', 3, 3, TRUE),
  (13, 'Walid',   'Kacem',      'walid.kacem@example.com',     'FAKE_HASH_replace_me', '2001-09-23', 'M', 2, 4, TRUE),
  (14, 'Salma',   'Ayari',      'salma.ayari@example.com',     'FAKE_HASH_replace_me', '2003-03-08', 'F', 4, 4, TRUE),
  (15, 'Aymen',   'Thabet',     'aymen.thabet@example.com',    'FAKE_HASH_replace_me', '2003-12-01', 'M', 4, 4, TRUE),
  (16, 'Mariem',  'Souissi',    'mariem.souissi@example.com',  'FAKE_HASH_replace_me', '2004-01-15', 'F', 4, 1, TRUE),
  (17, 'Bilel',   'Karray',     'bilel.karray@example.com',    'FAKE_HASH_replace_me', '2002-05-27', 'M', 4, 2, FALSE),
  (18, 'Emna',    'Louati',     'emna.louati@example.com',     'FAKE_HASH_replace_me', '2004-02-11', 'F', 4, 3, TRUE),
  (19, 'Fares',   'Belhaj',     'fares.belhaj@example.com',    'FAKE_HASH_replace_me', '2003-07-07', 'M', 4, NULL, TRUE),
  (20, 'Hana',    'Mejri',      'hana.mejri@example.com',      'FAKE_HASH_replace_me', '2004-04-20', 'F', 4, 4, TRUE);

-- ---------- Friendships ----------
-- user1_id < user2_id always; requester_id is one of the two.
INSERT INTO friendships (id, user1_id, user2_id, requester_id, status, created_at) VALUES
  -- accepted
  (1,  1,  2,  1,  'accepted', NOW() - INTERVAL '30 days'),
  (2,  1,  3,  3,  'accepted', NOW() - INTERVAL '29 days'),
  (3,  2,  3,  2,  'accepted', NOW() - INTERVAL '28 days'),
  (4,  2,  4,  4,  'accepted', NOW() - INTERVAL '27 days'),
  (5,  2,  5,  2,  'accepted', NOW() - INTERVAL '26 days'),
  (6,  2,  9,  9,  'accepted', NOW() - INTERVAL '25 days'),
  (7,  3,  16, 16, 'accepted', NOW() - INTERVAL '24 days'),
  (8,  5,  6,  6,  'accepted', NOW() - INTERVAL '23 days'),
  (9,  5,  7,  5,  'accepted', NOW() - INTERVAL '22 days'),
  (10, 5,  8,  8,  'accepted', NOW() - INTERVAL '21 days'),
  (11, 6,  7,  7,  'accepted', NOW() - INTERVAL '20 days'),
  (12, 9,  10, 9,  'accepted', NOW() - INTERVAL '19 days'),
  (13, 9,  12, 12, 'accepted', NOW() - INTERVAL '18 days'),
  (14, 10, 11, 10, 'accepted', NOW() - INTERVAL '17 days'),
  (15, 10, 18, 18, 'accepted', NOW() - INTERVAL '16 days'),
  (16, 13, 14, 13, 'accepted', NOW() - INTERVAL '15 days'),
  (17, 14, 15, 15, 'accepted', NOW() - INTERVAL '14 days'),
  (18, 14, 20, 20, 'accepted', NOW() - INTERVAL '13 days'),
  -- pending
  (19, 1,  19, 19, 'pending',  NOW() - INTERVAL '3 days'),
  (20, 3,  4,  3,  'pending',  NOW() - INTERVAL '2 days'),
  (21, 7,  17, 7,  'pending',  NOW() - INTERVAL '5 days'),
  (22, 11, 12, 11, 'pending',  NOW() - INTERVAL '1 day'),
  (23, 15, 20, 15, 'pending',  NOW() - INTERVAL '6 hours'),
  -- blocked
  (24, 4,  16, 16, 'blocked',  NOW() - INTERVAL '10 days'),
  (25, 8,  19, 19, 'blocked',  NOW() - INTERVAL '9 days');

-- ---------- Conversations (only between accepted friends) ----------
INSERT INTO conversations (id, user1_id, user2_id, created_at) VALUES
  (1, 1,  2,  NOW() - INTERVAL '29 days'),
  (2, 1,  3,  NOW() - INTERVAL '28 days'),
  (3, 2,  3,  NOW() - INTERVAL '27 days'),
  (4, 2,  5,  NOW() - INTERVAL '25 days'),
  (5, 5,  6,  NOW() - INTERVAL '22 days'),
  (6, 5,  7,  NOW() - INTERVAL '21 days'),
  (7, 9,  10, NOW() - INTERVAL '18 days'),
  (8, 10, 11, NOW() - INTERVAL '16 days'),
  (9, 13, 14, NOW() - INTERVAL '14 days');

-- ---------- Messages ----------
INSERT INTO messages (conversation_id, sender_id, content, created_at, is_read) VALUES
  -- conv 1: Amine <-> Sarra
  (1, 1,  'Hey Sarra, ready for the hackathon demo?',                 NOW() - INTERVAL '2 days 3 hours', TRUE),
  (1, 2,  'Almost! Just polishing the feed page.',                    NOW() - INTERVAL '2 days 2 hours', TRUE),
  (1, 1,  'Nice. Ping me when it is deployed.',                       NOW() - INTERVAL '2 days 1 hour',  TRUE),
  (1, 2,  'Will do 👍',                                                NOW() - INTERVAL '2 days',         FALSE),
  -- conv 2: Amine <-> Youssef
  (2, 3,  'Can I get access to the admin panel?',                     NOW() - INTERVAL '1 day 5 hours',  TRUE),
  (2, 1,  'Sure, I will update your role tomorrow.',                  NOW() - INTERVAL '1 day 4 hours',  TRUE),
  (2, 3,  'Thanks!',                                                  NOW() - INTERVAL '1 day 3 hours',  TRUE),
  -- conv 3: Sarra <-> Youssef
  (3, 2,  'Blitz tournament Friday, are you in?',                     NOW() - INTERVAL '20 hours',       TRUE),
  (3, 3,  'Absolutely. Same time as last week?',                      NOW() - INTERVAL '19 hours',       TRUE),
  (3, 2,  'Yes, 5pm in room 12.',                                     NOW() - INTERVAL '18 hours',       FALSE),
  -- conv 4: Sarra <-> Mehdi (two presidents)
  (4, 5,  'Can we share the main hall on Thursday?',                  NOW() - INTERVAL '4 days',         TRUE),
  (4, 2,  'Chess can take the morning, robotics the afternoon?',      NOW() - INTERVAL '4 days' + INTERVAL '10 minutes', TRUE),
  (4, 5,  'Deal.',                                                    NOW() - INTERVAL '4 days' + INTERVAL '15 minutes', TRUE),
  -- conv 5: Mehdi <-> Nour
  (5, 6,  'The line follower finally works!',                         NOW() - INTERVAL '3 days',         TRUE),
  (5, 5,  'Great job. Post it on the feed!',                          NOW() - INTERVAL '3 days' + INTERVAL '5 minutes', TRUE),
  (5, 6,  'Already did :)',                                           NOW() - INTERVAL '3 days' + INTERVAL '8 minutes', TRUE),
  -- conv 6: Mehdi <-> Karim
  (6, 5,  'Karim, did you order the motors?',                         NOW() - INTERVAL '8 hours',        TRUE),
  (6, 7,  'Ordered yesterday, arriving Monday.',                      NOW() - INTERVAL '7 hours',        FALSE),
  -- conv 7: Omar <-> Lina
  (7, 9,  'Open mic is confirmed for next week.',                     NOW() - INTERVAL '2 days',         TRUE),
  (7, 10, 'Perfect, I will prepare two songs.',                       NOW() - INTERVAL '2 days' + INTERVAL '30 minutes', TRUE),
  -- conv 8: Lina <-> Hedi
  (8, 11, 'Can I borrow your guitar amp?',                            NOW() - INTERVAL '5 hours',        FALSE),
  (8, 11, 'Only for Saturday.',                                       NOW() - INTERVAL '5 hours' + INTERVAL '1 minute', FALSE),
  -- conv 9: Walid <-> Salma
  (9, 13, 'Photo walk on Saturday, bring your camera.',               NOW() - INTERVAL '1 day',          TRUE),
  (9, 14, 'Which meeting point?',                                     NOW() - INTERVAL '23 hours',       TRUE),
  (9, 13, 'Main gate, 9am.',                                          NOW() - INTERVAL '22 hours',       FALSE);

-- ---------- Posts ----------
-- visible_role_id / visible_club_id: 0 = everyone.
-- role 2 = president, role 3 = vice_president.
INSERT INTO posts (id, user_id, content, visible_role_id, visible_club_id, created_at) VALUES
  (1,  2,  'Welcome to the Chess Club hackathon edition!',                    0, 0, NOW() - INTERVAL '10 days'),
  (2,  5,  'Robotics club meeting Thursday 6pm, lab B.',                      0, 2, NOW() - INTERVAL '9 days'),
  (3,  9,  'Music club open mic next week. Sign up with me!',                 0, 3, NOW() - INTERVAL '8 days'),
  (4,  1,  'Presidents only: budget meeting Friday at 4pm.',                  2, 0, NOW() - INTERVAL '7 days'),
  (5,  13, 'Photo walk this Saturday, meet at the main gate.',                0, 4, NOW() - INTERVAL '6 days'),
  (6,  3,  'Anyone up for a blitz tournament?',                               0, 1, NOW() - INTERVAL '6 days'),
  (7,  6,  'Our line-follower robot finally works! 🤖',                        0, 0, NOW() - INTERVAL '3 days'),
  (8,  10, 'Working on a new song, feedback welcome.',                        0, 3, NOW() - INTERVAL '3 days'),
  (9,  14, 'Golden hour shots from yesterday.',                               0, 0, NOW() - INTERVAL '2 days'),
  (10, 1,  'Platform announcement: the new chat feature is live!',            0, 0, NOW() - INTERVAL '2 days'),
  (11, 2,  'Chess president notes: semester sign-up sheet is open.',          2, 1, NOW() - INTERVAL '1 day 12 hours'),
  (12, 19, 'Hi everyone, I am new here and looking for a club!',              0, 0, NOW() - INTERVAL '1 day'),
  (13, 20, 'Anyone has tips for night photography?',                          0, 4, NOW() - INTERVAL '20 hours'),
  (14, 7,  'Looking for teammates for the hackathon, I can do hardware.',     0, 0, NOW() - INTERVAL '12 hours'),
  (15, 12, 'Music VP note: rehearsal room booking opens Monday.',             3, 3, NOW() - INTERVAL '6 hours');

-- ---------- Likes (one per user per post) ----------
INSERT INTO likes (user_id, post_id, created_at) VALUES
  (1, 1,  NOW() - INTERVAL '9 days'),
  (3, 1,  NOW() - INTERVAL '9 days'),
  (4, 1,  NOW() - INTERVAL '8 days'),
  (16,1,  NOW() - INTERVAL '8 days'),
  (6, 2,  NOW() - INTERVAL '8 days'),
  (7, 2,  NOW() - INTERVAL '8 days'),
  (8, 2,  NOW() - INTERVAL '8 days'),
  (10,3,  NOW() - INTERVAL '7 days'),
  (11,3,  NOW() - INTERVAL '7 days'),
  (12,3,  NOW() - INTERVAL '7 days'),
  (18,3,  NOW() - INTERVAL '6 days'),
  (2, 4,  NOW() - INTERVAL '6 days'),
  (5, 4,  NOW() - INTERVAL '6 days'),
  (9, 4,  NOW() - INTERVAL '6 days'),
  (14,5,  NOW() - INTERVAL '5 days'),
  (15,5,  NOW() - INTERVAL '5 days'),
  (20,5,  NOW() - INTERVAL '5 days'),
  (2, 6,  NOW() - INTERVAL '5 days'),
  (16,6,  NOW() - INTERVAL '5 days'),
  (5, 7,  NOW() - INTERVAL '3 days'),
  (7, 7,  NOW() - INTERVAL '3 days'),
  (8, 7,  NOW() - INTERVAL '2 days'),
  (1, 7,  NOW() - INTERVAL '2 days'),
  (19,7,  NOW() - INTERVAL '2 days'),
  (9, 8,  NOW() - INTERVAL '2 days'),
  (11,8,  NOW() - INTERVAL '2 days'),
  (13,9,  NOW() - INTERVAL '1 day'),
  (15,9,  NOW() - INTERVAL '1 day'),
  (20,9,  NOW() - INTERVAL '1 day'),
  (2, 10, NOW() - INTERVAL '1 day'),
  (3, 10, NOW() - INTERVAL '1 day'),
  (5, 10, NOW() - INTERVAL '1 day'),
  (9, 10, NOW() - INTERVAL '1 day'),
  (14,10, NOW() - INTERVAL '20 hours'),
  (3, 11, NOW() - INTERVAL '1 day'),
  (1, 12, NOW() - INTERVAL '20 hours'),
  (2, 12, NOW() - INTERVAL '20 hours'),
  (6, 14, NOW() - INTERVAL '8 hours'),
  (5, 14, NOW() - INTERVAL '8 hours'),
  (9, 15, NOW() - INTERVAL '3 hours');

-- ---------- Comments ----------
INSERT INTO comments (id, post_id, user_id, content, created_at) VALUES
  (1,  1,  3,  'Excited to be here!',                              NOW() - INTERVAL '9 days'),
  (2,  1,  16, 'Count me in for the first tournament.',            NOW() - INTERVAL '8 days'),
  (3,  2,  6,  'I will bring the new sensors.',                    NOW() - INTERVAL '8 days'),
  (4,  3,  10, 'I am signing up!',                                 NOW() - INTERVAL '7 days'),
  (5,  3,  11, 'Can I play drums?',                                NOW() - INTERVAL '7 days'),
  (6,  3,  9,  '@Hedi yes, drums are welcome.',                    NOW() - INTERVAL '7 days' + INTERVAL '1 hour'),
  (7,  4,  5,  'I will be there.',                                 NOW() - INTERVAL '6 days'),
  (8,  5,  14, 'Which lens do you recommend?',                     NOW() - INTERVAL '5 days'),
  (9,  5,  13, 'A 50mm is perfect for it.',                        NOW() - INTERVAL '5 days' + INTERVAL '30 minutes'),
  (10, 7,  5,  'Great work team!',                                 NOW() - INTERVAL '3 days'),
  (11, 7,  1,  'Impressive. Share a video next time.',             NOW() - INTERVAL '2 days'),
  (12, 7,  19, 'Looks awesome, how long did it take?',             NOW() - INTERVAL '2 days'),
  (13, 7,  6,  '@Fares about two weeks of evenings.',              NOW() - INTERVAL '2 days' + INTERVAL '2 hours'),
  (14, 10, 3,  'The chat works great!',                            NOW() - INTERVAL '1 day'),
  (15, 12, 2,  'Welcome Fares! Come to the Chess Club on Friday.', NOW() - INTERVAL '20 hours'),
  (16, 12, 1,  'Welcome! Send me a message if you need help.',     NOW() - INTERVAL '19 hours'),
  (17, 13, 14, 'Use a tripod and a slow shutter speed.',           NOW() - INTERVAL '18 hours'),
  (18, 14, 6,  'I can do the software side.',                      NOW() - INTERVAL '8 hours'),
  (19, 15, 10, 'Thanks Rim!',                                      NOW() - INTERVAL '3 hours');

-- ---------- Notifications (one row per recipient) ----------
-- Broadcast from the admin to every active user.
INSERT INTO notifications (title, content, created_by, recipient_id, seen, created_at)
SELECT 'Welcome to the platform',
       'Thanks for joining! Create posts, make friends and chat.',
       1, u.id, (u.id % 3 = 0), NOW() - INTERVAL '12 days'
FROM users u
WHERE u.is_active = TRUE;

-- Targeted: presidents only (role_id = 2), all clubs.
INSERT INTO notifications (title, content, created_by, recipient_id, seen, created_at)
SELECT 'Presidents meeting',
       'Budget meeting on Friday at 4pm. Please bring your club budget.',
       1, u.id, (u.id = 2), NOW() - INTERVAL '7 days'
FROM users u
WHERE u.role_id = 2 AND u.is_active = TRUE;

-- Targeted: Robotics club members only (club_id = 2).
INSERT INTO notifications (title, content, created_by, recipient_id, seen, created_at)
SELECT 'Robotics lab schedule',
       'The lab is open every Thursday from 6pm to 9pm.',
       5, u.id, FALSE, NOW() - INTERVAL '9 days'
FROM users u
WHERE u.club_id = 2 AND u.is_active = TRUE AND u.id <> 5;

-- System-generated (created_by NULL) and individual notifications.
INSERT INTO notifications (title, content, created_by, recipient_id, seen, created_at) VALUES
  ('New friend request',  'Fares Belhaj sent you a friend request.',  NULL, 1,  FALSE, NOW() - INTERVAL '3 days'),
  ('New friend request',  'Youssef Mansour sent you a friend request.', NULL, 4, FALSE, NOW() - INTERVAL '2 days'),
  ('New friend request',  'Rim Dridi sent you... no, Hedi Zouari sent you a friend request.', NULL, 12, FALSE, NOW() - INTERVAL '1 day'),
  ('New friend request',  'Aymen Thabet sent you a friend request.',  NULL, 20, FALSE, NOW() - INTERVAL '6 hours'),
  ('Your post got likes', 'Your post "Our line-follower robot finally works!" has 5 likes.', NULL, 6, TRUE, NOW() - INTERVAL '2 days'),
  ('New comment',         'Fares Belhaj commented on your post.',     NULL, 6,  FALSE, NOW() - INTERVAL '2 days'),
  ('New comment',         'Sarra Trabelsi commented on your post.',   NULL, 19, FALSE, NOW() - INTERVAL '20 hours'),
  ('New message',         'Hedi Zouari sent you a message.',          NULL, 10, FALSE, NOW() - INTERVAL '5 hours'),
  ('New message',         'Walid Kacem sent you a message.',          NULL, 14, FALSE, NOW() - INTERVAL '22 hours'),
  ('Account notice',      'Your account has been deactivated.',       1,    17, FALSE, NOW() - INTERVAL '4 days');

-- ---------- Events (outbox) ----------
INSERT INTO events (event_type, payload, status, attempts, created_at, locked_at, processed_at, next_retry_at, last_error) VALUES
  ('post.created',   '{"post_id": 10, "user_id": 1}',            'processed',  1, NOW() - INTERVAL '2 days',    NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days', NULL),
  ('post.created',   '{"post_id": 12, "user_id": 19}',           'processed',  1, NOW() - INTERVAL '1 day',     NOW() - INTERVAL '1 day',    NOW() - INTERVAL '1 day',    NOW() - INTERVAL '1 day',  NULL),
  ('comment.created','{"comment_id": 13, "post_id": 7}',         'processed',  1, NOW() - INTERVAL '2 days',    NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days', NULL),
  ('like.created',   '{"post_id": 7, "user_id": 19}',            'processed',  2, NOW() - INTERVAL '2 days',    NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days',   NOW() - INTERVAL '2 days', 'timeout on first attempt'),
  ('friend.requested','{"friendship_id": 23}',                   'pending',    0, NOW() - INTERVAL '6 hours',   NULL,                        NULL,                        NOW() - INTERVAL '6 hours', NULL),
  ('message.sent',   '{"conversation_id": 8, "sender_id": 11}',  'pending',    0, NOW() - INTERVAL '5 hours',   NULL,                        NULL,                        NOW() - INTERVAL '5 hours', NULL),
  ('post.created',   '{"post_id": 15, "user_id": 12}',           'pending',    0, NOW() - INTERVAL '6 hours',   NULL,                        NULL,                        NOW() - INTERVAL '6 hours', NULL),
  ('post.created',   '{"post_id": 14, "user_id": 7}',            'pending',    2, NOW() - INTERVAL '12 hours',  NULL,                        NULL,                        NOW() + INTERVAL '10 minutes', 'notification service unavailable'),
  ('post.created',   '{"post_id": 13, "user_id": 20}',           'processing', 1, NOW() - INTERVAL '20 hours',  NOW() - INTERVAL '2 hours',  NULL,                        NOW() - INTERVAL '20 hours', NULL),
  ('comment.created','{"comment_id": 99, "post_id": 9999}',      'failed',     5, NOW() - INTERVAL '3 days',    NULL,                        NULL,                        NOW() - INTERVAL '3 days', 'post 9999 does not exist');

-- ---------- Re-sync SERIAL sequences after explicit ids ----------
SELECT setval(pg_get_serial_sequence('roles',         'id'), (SELECT MAX(id) FROM roles));
SELECT setval(pg_get_serial_sequence('clubs',         'id'), (SELECT MAX(id) FROM clubs));
SELECT setval(pg_get_serial_sequence('users',         'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('posts',         'id'), (SELECT MAX(id) FROM posts));
SELECT setval(pg_get_serial_sequence('comments',      'id'), (SELECT MAX(id) FROM comments));
SELECT setval(pg_get_serial_sequence('friendships',   'id'), (SELECT MAX(id) FROM friendships));
SELECT setval(pg_get_serial_sequence('conversations', 'id'), (SELECT MAX(id) FROM conversations));
SELECT setval(pg_get_serial_sequence('messages',      'id'), (SELECT MAX(id) FROM messages));
SELECT setval(pg_get_serial_sequence('notifications', 'id'), (SELECT MAX(id) FROM notifications));

COMMIT;
