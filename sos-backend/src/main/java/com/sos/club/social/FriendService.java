package com.sos.club.social;

import com.sos.club.common.ApiException;
import com.sos.club.common.UserSummary;
import com.sos.club.events.EventTypes;
import com.sos.club.events.OutboxService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sos.club.common.Pairs.high;
import static com.sos.club.common.Pairs.low;

/**
 * Amities. Paires triees : user1_id = LEAST(a,b), user2_id = GREATEST(a,b).
 * requester_id garde le sens de la demande.
 */
@Service
public class FriendService {

    /** status vu depuis "me" : none | pending_sent | pending_received | accepted | blocked */
    public record FriendshipDto(Integer friendshipId, UserSummary user, String status, boolean requestedByMe, OffsetDateTime since) {}

    record Row(int id, int user1, int user2, int requester, String status) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxService outbox;

    public FriendService(NamedParameterJdbcTemplate jdbc, OutboxService outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    private Optional<Row> find(int a, int b) {
        return jdbc.query("""
                SELECT id, user1_id, user2_id, requester_id, status FROM friendships
                WHERE user1_id = :lo AND user2_id = :hi
                FOR UPDATE
                """, Map.of("lo", low(a, b), "hi", high(a, b)),
                (rs, i) -> new Row(rs.getInt("id"), rs.getInt("user1_id"), rs.getInt("user2_id"),
                        rs.getInt("requester_id"), rs.getString("status")))
                .stream().findFirst();
    }

    private void ensureUser(int id) {
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = :id AND is_active)", Map.of("id", id), Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw ApiException.notFound("Utilisateur introuvable");
    }

    /**
     * Envoie une demande. Si l'autre m'avait deja envoye une demande,
     * c'est traite comme une acceptation (cas "sens inverse" du guide).
     */
    @Transactional
    public FriendshipDto request(int me, int other) {
        if (me == other) throw ApiException.badRequest("Impossible de s'ajouter soi-meme");
        ensureUser(other);
        Optional<Row> existing = find(me, other);
        if (existing.isEmpty()) {
            Integer id = jdbc.queryForObject("""
                    INSERT INTO friendships (user1_id, user2_id, requester_id, status)
                    VALUES (:lo, :hi, :me, 'pending')
                    ON CONFLICT (user1_id, user2_id) DO UPDATE SET user1_id = EXCLUDED.user1_id
                    RETURNING id
                    """, Map.of("lo", low(me, other), "hi", high(me, other), "me", me), Integer.class);
            outbox.publish(EventTypes.FRIEND_REQUESTED, Map.of("friendship_id", id));
            return status(me, other);
        }
        Row f = existing.get();
        return switch (f.status()) {
            case "accepted" -> throw ApiException.conflict("Vous etes deja amis");
            case "blocked" -> throw ApiException.forbidden("Relation bloquee");
            default -> f.requester() == me
                    ? status(me, other)            // demande deja envoyee : idempotent
                    : accept(me, other);           // il m'avait demande : on accepte
        };
    }

    @Transactional
    public FriendshipDto accept(int me, int other) {
        Row f = find(me, other).orElseThrow(() -> ApiException.notFound("Aucune demande de cet utilisateur"));
        if (!"pending".equals(f.status()) || f.requester() == me) {
            throw ApiException.badRequest("Aucune demande en attente de cet utilisateur");
        }
        jdbc.update("UPDATE friendships SET status = 'accepted' WHERE id = :id", Map.of("id", f.id()));
        outbox.publish(EventTypes.FRIEND_ACCEPTED, Map.of("friendship_id", f.id(), "user_id", me));
        return status(me, other);
    }

    /** Refuser une demande, annuler la sienne, ou retirer un ami. */
    @Transactional
    public void remove(int me, int other) {
        int n = jdbc.update("""
                DELETE FROM friendships WHERE user1_id = :lo AND user2_id = :hi AND status IN ('pending','accepted')
                """, Map.of("lo", low(me, other), "hi", high(me, other)));
        if (n == 0) throw ApiException.notFound("Aucune relation a supprimer");
    }

    /** Bloque (symetrique, cf. limites du guide). */
    @Transactional
    public FriendshipDto block(int me, int other) {
        if (me == other) throw ApiException.badRequest("Impossible de se bloquer soi-meme");
        ensureUser(other);
        jdbc.update("""
                INSERT INTO friendships (user1_id, user2_id, requester_id, status)
                VALUES (:lo, :hi, :me, 'blocked')
                ON CONFLICT (user1_id, user2_id) DO UPDATE SET status = 'blocked', requester_id = EXCLUDED.requester_id
                """, Map.of("lo", low(me, other), "hi", high(me, other), "me", me));
        return status(me, other);
    }

    @Transactional
    public void unblock(int me, int other) {
        int n = jdbc.update("""
                DELETE FROM friendships WHERE user1_id = :lo AND user2_id = :hi AND status = 'blocked' AND requester_id = :me
                """, Map.of("lo", low(me, other), "hi", high(me, other), "me", me));
        if (n == 0) throw ApiException.notFound("Aucun blocage de votre part");
    }

    // ------------------------------------------------------------------ lectures

    private static final String LIST_SELECT = """
            SELECT f.id, f.requester_id, f.status, f.created_at,
                   o.id AS o_id, o.first_name AS o_first_name, o.last_name AS o_last_name,
                   r.name AS o_role_name, c.name AS o_club_name
            FROM friendships f
            JOIN users o ON o.id = CASE WHEN f.user1_id = :me THEN f.user2_id ELSE f.user1_id END
            LEFT JOIN roles r ON r.id = o.role_id
            LEFT JOIN clubs c ON c.id = o.club_id
            WHERE (f.user1_id = :me OR f.user2_id = :me)
            """;

    private RowMapper<FriendshipDto> mapper(int me) {
        return (rs, i) -> {
            String st = rs.getString("status");
            boolean byMe = rs.getInt("requester_id") == me;
            String view = "pending".equals(st) ? (byMe ? "pending_sent" : "pending_received") : st;
            return new FriendshipDto(rs.getInt("id"), UserSummary.from(rs, "o_"), view, byMe,
                    rs.getObject("created_at", OffsetDateTime.class));
        };
    }

    public List<FriendshipDto> friends(int me) {
        return jdbc.query(LIST_SELECT + " AND f.status = 'accepted' AND o.is_active ORDER BY o.first_name, o.last_name",
                Map.of("me", me), mapper(me));
    }

    public List<FriendshipDto> incoming(int me) {
        return jdbc.query(LIST_SELECT + " AND f.status = 'pending' AND f.requester_id <> :me ORDER BY f.created_at DESC",
                Map.of("me", me), mapper(me));
    }

    public List<FriendshipDto> outgoing(int me) {
        return jdbc.query(LIST_SELECT + " AND f.status = 'pending' AND f.requester_id = :me ORDER BY f.created_at DESC",
                Map.of("me", me), mapper(me));
    }

    public FriendshipDto status(int me, int other) {
        return jdbc.query(LIST_SELECT + " AND o.id = :other", Map.of("me", me, "other", other), mapper(me))
                .stream().findFirst()
                .orElseGet(() -> new FriendshipDto(null,
                        jdbc.query("""
                                SELECT o.id AS o_id, o.first_name AS o_first_name, o.last_name AS o_last_name,
                                       r.name AS o_role_name, c.name AS o_club_name
                                FROM users o LEFT JOIN roles r ON r.id = o.role_id LEFT JOIN clubs c ON c.id = o.club_id
                                WHERE o.id = :id
                                """, Map.of("id", other), (rs, i) -> UserSummary.from(rs, "o_"))
                                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Utilisateur introuvable")),
                        "none", false, null));
    }

    /** Requete 1 du guide : le chat n'est permis qu'entre amis. */
    public boolean areFriends(int a, int b) {
        Boolean ok = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM friendships
                               WHERE status = 'accepted' AND user1_id = :lo AND user2_id = :hi)
                """, Map.of("lo", low(a, b), "hi", high(a, b)), Boolean.class);
        return Boolean.TRUE.equals(ok);
    }
}
