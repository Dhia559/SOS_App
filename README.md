# SOS Network

A private social network for the SOS village community: members post announcements targeted by **role** and **club**, add friends, chat in real time and receive live notifications.

## Features

- **Sign-up with a role**: Président d'association, Membre d'association, Président de club, Membre de club. Association roles belong to every club.
- **Targeted feed**: each post is visible to everyone, one role, one club, or both. Photo and event (date, place) attachments, likes, comments, filtering by role, "À venir" panel.
- **Friends**: send / accept requests, block, searchable member list.
- **Real-time chat** between friends: send, edit, delete, read receipts.
- **Live notifications**: bell with unseen counter (new post, like, comment, friend request, message).

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.4, JdbcTemplate (plain SQL), JWT + BCrypt, WebSocket (STOMP) |
| Database | PostgreSQL 19 |
| Frontend | HTML / CSS / vanilla JavaScript, `@stomp/stompjs` |

## Project structure

```
SOS_App/
├── hackathon_schema.sql   # database schema (source of truth)
├── sos-backend/           # Spring Boot API  → see sos-backend/README.md for every endpoint
│   ├── db/02_seed.sql     # test data (20 users, posts, friendships, chats)
│   └── src/main/java/com/sos/club/
│       ├── auth/  user/  feed/  social/  chat/   # features (controller → service → SQL)
│       ├── notification/  events/                # notifications via a transactional outbox
│       └── security/  config/  common/
└── frontend/              # index, login, signup, feed, conversations pages
```

## Getting started

**1. Database**: on an empty database, create the tables and (optionally) the test data:

```powershell
$env:PGPASSWORD = "0000"
$psql = "C:\Program Files\PostgreSQL\19\bin\psql.exe"
& $psql -h localhost -U postgres -d postgres -f hackathon_schema.sql
& $psql -h localhost -U postgres -d postgres -f sos-backend\db\02_seed.sql
```

Database created before the photo/event feature? Run `sos-backend\db\03_post_media.sql` once (safe to re-run).

**2. Backend**: create `sos-backend/.env` from `.env.example` (DB settings, `JWT_SECRET`), then:

```powershell
cd sos-backend
mvn spring-boot:run          # http://localhost:8080
```

**3. Test accounts**: the seed has fake passwords; set them all to `password123` once:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/dev/reset-passwords -ContentType "application/json" -Body '{"password":"password123"}'
```

**4. Frontend**: serve the folder (any static server):

```powershell
npx serve frontend           # http://localhost:3000
```

Log in with `amine.bensalah@example.com` / `password123`, or create an account.

## How it works (in short)

- **REST API** under `/api/...`, authenticated with `Authorization: Bearer <JWT>`.
- **Visibility** is computed in SQL: a post is shown if its target role/club is `0` (everyone) or matches the viewer.
- **Notifications** use a *transactional outbox*: the data and an `events` row are saved in the same transaction, then processed in the background (with retries), so none are lost or duplicated.
- **Real time** over WebSocket at `/ws`: each user receives only their own messages and notifications.

## Before a real deployment

Set `DEV_ENDPOINTS=false` (the password-reset endpoint is public), restrict CORS to the frontend's address, and replace `LoggingPushSender` with a real push service (e.g. Firebase) for mobile notifications.
