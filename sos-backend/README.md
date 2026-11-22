# SOS Club Social App — Backend

Java 21 · Spring Boot 3.4 · PostgreSQL 18 · JDBC (JdbcTemplate) · JWT · WebSocket STOMP

## Demarrage

1. Remplir `.env` (copie de `.env.example`) : `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.
2. Creer la base + schema + seed :
   ```powershell
   powershell -ExecutionPolicy Bypass -File db\setup-db.ps1          # -Reset pour tout recreer, -NoSeed sans donnees
   ```
3. Lancer (depuis la racine du projet, pour que `.env` soit lu) :
   ```powershell
   mvn spring-boot:run
   ```
4. Les mots de passe du seed sont faux (`FAKE_HASH_replace_me`). Pour se connecter avec les 20 users de test :
   `POST /api/dev/reset-passwords` → tout le monde a `password123`.
   Exemple : `amine.bensalah@example.com` / `password123`.

`requests.http` contient des requetes pretes a l'emploi.

## Architecture

```
auth/          login, register (BCrypt + JWT)
user/          profil, recherche, roles/clubs, device_tokens
feed/          feed cible, posts, likes, commentaires
social/        amities (paires triees, requester_id)
chat/          conversations 1-1, messages, push WebSocket
events/        outbox : OutboxService, listener, EventProcessor, EventWorker
notification/  notifications, handlers par type d'event, push WS + mobile
```

### Flux post → notification (transactional outbox)

```
POST /api/posts
  └─ @Transactional : INSERT posts  +  INSERT events('post.created', {post_id, user_id})  → COMMIT
        └─ AFTER_COMMIT → OutboxDispatchListener (@Async)
              └─ EventProcessor.dispatchNow(id) : claim → handler → INSERT notifications
                 + mark 'processed' (meme transaction) → push WebSocket / mobile
EventWorker (toutes les 2 s) : reprend les events 'pending' (rates, retries, seed)
            reaper (1 min) : 'processing' bloques > 5 min → 'pending'
Echec : backoff 30s, 60s, 120s... → 'failed' apres 5 tentatives
```

Meme principe pour `like.created`, `comment.created`, `friend.requested`, `friend.accepted`, `message.sent`.
Payloads (identiques au seed) : voir `events/EventTypes.java`.

| Event | Notifie |
|---|---|
| post.created | l'audience du post (users actifs du role/club cible, sauf l'auteur) |
| like.created | l'auteur du post (sauf s'il like son propre post) |
| comment.created | l'auteur du post (sauf s'il commente son propre post) |
| friend.requested | le destinataire de la demande |
| friend.accepted | celui qui avait demande |
| message.sent | l'autre participant |

## Authentification

Header `Authorization: Bearer <token>` sur toutes les routes sauf `/api/auth/**`, `GET /api/roles`, `GET /api/clubs`, `/api/dev/**`.

## Endpoints

### Auth / users
| Methode | URL | Body / params |
|---|---|---|
| POST | /api/auth/register | firstName, lastName, email, password, birthday?, sex? (M/F), roleId?, clubId? |
| POST | /api/auth/login | email, password → `{token, tokenType, expiresIn, user}` |
| GET | /api/users/me | |
| PUT | /api/users/me | firstName?, lastName?, birthday?, sex? |
| GET | /api/users/{id} | |
| GET | /api/users?search=&limit= | recherche par nom / email |
| GET | /api/users/{id}/posts?before= | posts visibles d'un user |
| GET | /api/roles, /api/clubs | id 0 = ALL |
| POST | /api/devices | `{token}` token push de l'appareil |
| DELETE | /api/devices?token= | au logout |

### Feed
| Methode | URL | Notes |
|---|---|---|
| GET | /api/feed?before=&limit= | `{posts, nextBefore}` ; page suivante : `before=nextBefore` (URL-encoder le `+` du fuseau) |
| POST | /api/posts | `{content, visibleRoleId?, visibleClubId?, image?, eventTitle?, eventStartsAt?, eventPlace?}` (0/absent = tous ; `image` = data URL, 5 Mo max ; titre + date ensemble) → event post.created |
| GET | /api/posts/{id}/image | photo du post (meme visibilite que le post) |
| GET | /api/feed/events?limit= | evenements a venir des posts visibles (panneau « A venir ») |
| GET / PUT / DELETE | /api/posts/{id} | PUT/DELETE : auteur seulement |
| POST / DELETE | /api/posts/{id}/like | → `{postId, liked, likeCount}` ; POST → event like.created |
| GET | /api/posts/{id}/likes | qui a aime |
| GET / POST | /api/posts/{id}/comments | POST `{content}` → event comment.created |
| DELETE | /api/comments/{id} | auteur du commentaire ou du post |

Chaque post : `id, content, author{id,firstName,lastName,roleName,clubName}, visibleRoleId/Name, visibleClubId/Name, createdAt, likeCount, commentCount, likedByMe`.

### Amis
| Methode | URL | Notes |
|---|---|---|
| GET | /api/friends | amis acceptes |
| GET | /api/friends/requests | demandes recues |
| GET | /api/friends/requests/sent | demandes envoyees |
| GET | /api/friends/{userId} | statut : none, pending_sent, pending_received, accepted, blocked |
| POST | /api/friends/{userId} | envoyer une demande (accepte si l'autre avait deja demande) |
| POST | /api/friends/{userId}/accept | |
| DELETE | /api/friends/{userId} | refuser / annuler / retirer |
| POST / DELETE | /api/friends/{userId}/block | bloquer / debloquer |

### Chat (amis uniquement)
| Methode | URL | Notes |
|---|---|---|
| GET | /api/conversations | inbox : otherUser, lastMessage, unreadCount |
| GET | /api/conversations/unread-count | total des messages non lus |
| POST | /api/conversations/with/{userId} | ouvre ou retrouve la conversation |
| GET | /api/conversations/{id} | |
| GET | /api/conversations/{id}/messages?before=&limit= | ordre chronologique ; `before` = id du plus ancien message |
| POST | /api/conversations/{id}/messages | `{content}` → event message.sent + push WS |
| PUT | /api/conversations/{id}/messages/{messageId} | `{content}` → auteur seulement ; push WS `/user/queue/messages.updated` |
| DELETE | /api/conversations/{id}/messages/{messageId} | auteur seulement ; push WS `/user/queue/messages.deleted` `{conversationId, messageId}` |
| PUT | /api/conversations/{id}/read | marque les messages recus comme lus |

### Notifications
| Methode | URL | Notes |
|---|---|---|
| GET | /api/notifications?before=&unseenOnly=&limit= | `before` = id |
| GET | /api/notifications/unseen-count | `{count}` |
| PUT | /api/notifications/{id}/seen | |
| PUT | /api/notifications/seen-all | |
| DELETE | /api/notifications/{id} | |

### Debug events
`GET /api/internal/events?status=pending|processing|processed|failed` · `POST /api/internal/events/{id}/dispatch` · `POST /api/internal/events/process`

## WebSocket (temps reel)

- URL : `ws://localhost:8080/ws` (STOMP natif) ou `http://localhost:8080/ws-sockjs` (SockJS)
- Frame CONNECT avec le header `Authorization: Bearer <token>`
- S'abonner a :
  - `/user/queue/notifications` (nouvelle notification)
  - `/user/queue/notifications.count` (`{count}`)
  - `/user/queue/messages` (nouveau message, pour les 2 participants)
  - `/user/queue/messages.updated` / `/user/queue/messages.deleted` (message modifie / supprime)
  - `/user/queue/read` (accuse de lecture)
  - `/user/queue/errors`
- Envoyer : `/app/chat.send` `{conversationId, content}` · `/app/chat.read` `{conversationId}`

Exemple JS (`@stomp/stompjs`) :
```js
const client = new Client({ brokerURL: "ws://localhost:8080/ws",
  connectHeaders: { Authorization: "Bearer " + token } });
client.onConnect = () => {
  client.subscribe("/user/queue/notifications", m => console.log(JSON.parse(m.body)));
  client.subscribe("/user/queue/messages", m => console.log(JSON.parse(m.body)));
};
client.activate();
```

## Push mobile

`device_tokens` est rempli par `POST /api/devices`. L'envoi passe par l'interface `PushSender`.
L'implementation par defaut (`LoggingPushSender`) ne fait que logger : pour du vrai push (FCM),
il suffit d'ajouter un bean `@Primary` qui implemente `PushSender`.
