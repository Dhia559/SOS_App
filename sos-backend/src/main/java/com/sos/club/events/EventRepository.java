package com.sos.club.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Requetes 7 a 10 du guide (claim, succes, echec, reaper). */
@Repository
public class EventRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<EventRow> mapper;

    public EventRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.mapper = (rs, i) -> {
            try {
                return new EventRow(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        om.readTree(rs.getString("payload")),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("locked_at", OffsetDateTime.class),
                        rs.getObject("processed_at", OffsetDateTime.class),
                        rs.getObject("next_retry_at", OffsetDateTime.class),
                        rs.getString("last_error"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("payload JSON invalide pour l'event " + rs.getLong("id"), e);
            }
        };
    }

    /** Query 7 : reclame un lot. SKIP LOCKED => plusieurs workers sans collision. */
    public List<EventRow> claimBatch(int limit) {
        return jdbc.query("""
                WITH next AS (
                    SELECT id FROM events
                    WHERE status = 'pending' AND next_retry_at <= NOW()
                    ORDER BY created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE events e
                SET status = 'processing', locked_at = NOW()
                FROM next
                WHERE e.id = next.id
                RETURNING e.*
                """, Map.of("limit", limit), mapper);
    }

    /** Claim d'un event precis (dispatch immediat apres commit). */
    public List<EventRow> claimOne(long id) {
        return jdbc.query("""
                UPDATE events SET status = 'processing', locked_at = NOW()
                WHERE id = :id AND status = 'pending' AND next_retry_at <= NOW()
                RETURNING *
                """, Map.of("id", id), mapper);
    }

    /** Query 8. Retourne 0 si l'event n'est plus a nous (reape par un autre worker). */
    public int markProcessed(long id) {
        return jdbc.update("""
                UPDATE events
                SET status = 'processed', processed_at = NOW(), locked_at = NULL, last_error = NULL
                WHERE id = :id AND status = 'processing'
                """, Map.of("id", id));
    }

    /** Query 9 : backoff exponentiel 30s, 60s, 120s... puis 'failed'. */
    public int markFailed(long id, String error, int maxAttempts) {
        return jdbc.update("""
                UPDATE events
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= :max THEN 'failed' ELSE 'pending' END,
                    next_retry_at = NOW() + (INTERVAL '30 seconds' * POWER(2, attempts)),
                    locked_at = NULL,
                    last_error = :error
                WHERE id = :id AND status = 'processing'
                """, Map.of("id", id, "error", error, "max", maxAttempts));
    }

    /** Query 10 : events bloques en 'processing' depuis plus de 5 minutes. */
    public int reap() {
        return jdbc.update("""
                UPDATE events SET status = 'pending', locked_at = NULL
                WHERE status = 'processing' AND locked_at < NOW() - INTERVAL '5 minutes'
                """, Map.of());
    }

    /** Remet un event 'failed' en file (retry manuel). */
    public int requeue(long id) {
        return jdbc.update("""
                UPDATE events SET status = 'pending', attempts = 0, next_retry_at = NOW(), locked_at = NULL
                WHERE id = :id AND status = 'failed'
                """, Map.of("id", id));
    }

    /** Purge des events traites (sinon la table grossit indefiniment). */
    public int purgeProcessedOlderThanDays(int days) {
        return jdbc.update("""
                DELETE FROM events WHERE status = 'processed' AND processed_at < NOW() - make_interval(days => :d)
                """, Map.of("d", days));
    }

    public List<EventRow> list(String status, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("limit", limit).addValue("status", status);
        String where = status == null ? "" : "WHERE status = :status ";
        return jdbc.query("SELECT * FROM events " + where + "ORDER BY id DESC LIMIT :limit", p, mapper);
    }
}
