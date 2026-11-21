package com.sos.club.chat;

import com.sos.club.chat.ChatDtos.*;
import com.sos.club.common.ApiException;
import com.sos.club.common.UserSummary;
import com.sos.club.events.EventTypes;
import com.sos.club.events.OutboxService;
import com.sos.club.social.FriendService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.sos.club.common.Pairs.high;
import static com.sos.club.common.Pairs.low;

/**
 * Chat 1-a-1. Regles que la BD n'impose pas et que l'on verifie ici :
 *  - amitie 'accepted' obligatoire pour ouvrir une conversation et envoyer
 *  - sender_id doit etre l'un des deux users de la conversation
 */
@Service
public class ChatService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FriendService friends;
    private final OutboxService outbox;
    private final ApplicationEventPublisher publisher;

    public ChatService(NamedParameterJdbcTemplate jdbc, FriendService friends, OutboxService outbox,
                       ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.friends = friends;
        this.outbox = outbox;
        this.publisher = publisher;
    }

    static final RowMapper<MessageDto> MSG = (rs, i) -> new MessageDto(
            rs.getInt("id"), rs.getInt("conversation_id"), rs.getInt("sender_id"), rs.getString("content"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getBoolean("is_read"));

    record Pair(int user1, int user2) {
        boolean has(int u) { return u == user1 || u == user2; }
        int other(int u) { return u == user1 ? user2 : user1; }
    }

    /** Verifie que "me" fait partie de la conversation et renvoie la paire. */
    private Pair membership(int me, int conversationId) {
        Pair p = jdbc.query("SELECT user1_id, user2_id FROM conversations WHERE id = :id", Map.of("id", conversationId),
                        (rs, i) -> new Pair(rs.getInt("user1_id"), rs.getInt("user2_id")))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Conversation introuvable"));
        if (!p.has(me)) throw ApiException.notFound("Conversation introuvable");
        return p;
    }

    /** Requetes 1 et 2 du guide : verifie l'amitie puis find-or-create (renvoie toujours l'id). */
    @Transactional
    public ConversationDto open(int me, int other) {
        if (me == other) throw ApiException.badRequest("Impossible de discuter avec soi-meme");
        if (!friends.areFriends(me, other)) throw ApiException.forbidden("Vous devez etre amis pour discuter");
        Integer id = jdbc.queryForObject("""
                INSERT INTO conversations (user1_id, user2_id)
                VALUES (:lo, :hi)
                ON CONFLICT (user1_id, user2_id) DO UPDATE SET user1_id = EXCLUDED.user1_id
                RETURNING id
                """, Map.of("lo", low(me, other), "hi", high(me, other)), Integer.class);
        return conversation(me, id);
    }

    private static final String INBOX = """
            SELECT c.id, c.created_at,
                   o.id AS o_id, o.first_name AS o_first_name, o.last_name AS o_last_name,
                   r.name AS o_role_name, cl.name AS o_club_name,
                   lm.id AS lm_id, lm.sender_id AS lm_sender_id, lm.content AS lm_content,
                   lm.created_at AS lm_created_at, lm.is_read AS lm_is_read,
                   (SELECT COUNT(*) FROM messages m
                     WHERE m.conversation_id = c.id AND m.is_read = FALSE AND m.sender_id <> :me) AS unread
            FROM conversations c
            JOIN users o ON o.id = CASE WHEN c.user1_id = :me THEN c.user2_id ELSE c.user1_id END
            LEFT JOIN roles r ON r.id = o.role_id
            LEFT JOIN clubs cl ON cl.id = o.club_id
            LEFT JOIN LATERAL (
                SELECT id, sender_id, content, created_at, is_read FROM messages m
                WHERE m.conversation_id = c.id ORDER BY m.created_at DESC, m.id DESC LIMIT 1
            ) lm ON TRUE
            WHERE (c.user1_id = :me OR c.user2_id = :me)
            """;

    private static final RowMapper<ConversationDto> CONV = (rs, i) -> {
        MessageDto last = rs.getObject("lm_id") == null ? null : new MessageDto(
                rs.getInt("lm_id"), rs.getInt("id"), rs.getInt("lm_sender_id"), rs.getString("lm_content"),
                rs.getObject("lm_created_at", OffsetDateTime.class), rs.getBoolean("lm_is_read"));
        return new ConversationDto(rs.getInt("id"), UserSummary.from(rs, "o_"), last, rs.getLong("unread"),
                rs.getObject("created_at", OffsetDateTime.class));
    };

    public List<ConversationDto> inbox(int me) {
        return jdbc.query(INBOX + " ORDER BY COALESCE(lm.created_at, c.created_at) DESC", Map.of("me", me), CONV);
    }

    public ConversationDto conversation(int me, int conversationId) {
        membership(me, conversationId);
        return jdbc.query(INBOX + " AND c.id = :id", Map.of("me", me, "id", conversationId), CONV)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Conversation introuvable"));
    }

    /** Historique, ordre chronologique. Pagination : ?before=<id du plus ancien message affiche>. */
    public List<MessageDto> messages(int me, int conversationId, Integer beforeId, int limit) {
        membership(me, conversationId);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("c", conversationId).addValue("before", beforeId).addValue("limit", Math.min(Math.max(limit, 1), 100));
        String sql = "SELECT * FROM messages WHERE conversation_id = :c"
                + (beforeId != null ? " AND id < :before" : "")
                + " ORDER BY created_at DESC, id DESC LIMIT :limit";
        List<MessageDto> list = new ArrayList<>(jdbc.query(sql, p, MSG));
        Collections.reverse(list);
        return list;
    }

    @Transactional
    public MessageDto send(int me, int conversationId, String content) {
        if (content == null || content.isBlank()) throw ApiException.badRequest("Message vide");
        Pair pair = membership(me, conversationId);
        int other = pair.other(me);
        if (!friends.areFriends(me, other)) throw ApiException.forbidden("Vous n'etes plus amis avec cet utilisateur");

        MessageDto msg = jdbc.queryForObject("""
                INSERT INTO messages (conversation_id, sender_id, content) VALUES (:c, :me, :content)
                RETURNING *
                """, Map.of("c", conversationId, "me", me, "content", content.strip()), MSG);

        outbox.publish(EventTypes.MESSAGE_SENT,
                Map.of("conversation_id", conversationId, "sender_id", me, "message_id", msg.id()));
        publisher.publishEvent(new MessageCreated(msg, other)); // push WS apres commit
        return msg;
    }

    /** Message de la conversation dont "me" est l'auteur (404 sinon, 403 si c'est celui de l'autre). */
    private MessageDto ownMessage(int me, int conversationId, int messageId) {
        membership(me, conversationId);
        MessageDto msg = jdbc.query("SELECT * FROM messages WHERE id = :m AND conversation_id = :c",
                        Map.of("m", messageId, "c", conversationId), MSG)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Message introuvable"));
        if (msg.senderId() != me) throw ApiException.forbidden("Seul l'auteur peut modifier ou supprimer ce message");
        return msg;
    }

    @Transactional
    public MessageDto edit(int me, int conversationId, int messageId, String content) {
        if (content == null || content.isBlank()) throw ApiException.badRequest("Message vide");
        ownMessage(me, conversationId, messageId);
        MessageDto msg = jdbc.queryForObject("UPDATE messages SET content = :content WHERE id = :m RETURNING *",
                Map.of("m", messageId, "content", content.strip()), MSG);
        publisher.publishEvent(new MessageUpdated(msg, membership(me, conversationId).other(me)));
        return msg;
    }

    @Transactional
    public void delete(int me, int conversationId, int messageId) {
        ownMessage(me, conversationId, messageId);
        jdbc.update("DELETE FROM messages WHERE id = :m", Map.of("m", messageId));
        publisher.publishEvent(new MessageDeleted(conversationId, messageId, me, membership(me, conversationId).other(me)));
    }

    /** Marque comme lus les messages recus dans la conversation. */
    @Transactional
    public int markRead(int me, int conversationId) {
        Pair pair = membership(me, conversationId);
        int n = jdbc.update("""
                UPDATE messages SET is_read = TRUE
                WHERE conversation_id = :c AND sender_id <> :me AND is_read = FALSE
                """, Map.of("c", conversationId, "me", me));
        if (n > 0) publisher.publishEvent(new ReadReceiptEvent(new ReadReceipt(conversationId, me, n), pair.other(me)));
        return n;
    }

    public long unreadTotal(int me) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id
                WHERE (c.user1_id = :me OR c.user2_id = :me) AND m.sender_id <> :me AND m.is_read = FALSE
                """, Map.of("me", me), Long.class);
        return n == null ? 0 : n;
    }

    public record ReadReceiptEvent(ReadReceipt receipt, int notifyUserId) {}
}
