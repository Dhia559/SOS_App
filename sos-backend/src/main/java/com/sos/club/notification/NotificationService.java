package com.sos.club.notification;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NamedParameterJdbcTemplate jdbc;

    public NotificationService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    static final RowMapper<NotificationDto> MAPPER = (rs, i) -> new NotificationDto(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getObject("created_by", Integer.class),
            rs.getString("created_by_name"),
            rs.getInt("recipient_id"),
            rs.getBoolean("seen"),
            rs.getObject("created_at", OffsetDateTime.class));

    // CTE "ins" + jointure pour renvoyer aussi le nom de l'auteur.
    private static final String RETURN_WITH_NAME = """
            SELECT ins.*, (cu.first_name || ' ' || cu.last_name) AS created_by_name
            FROM ins LEFT JOIN users cu ON cu.id = ins.created_by
            """;

    // ---------- Ecriture (utilise par les handlers d'events) ----------

    /** Une notification pour un destinataire. */
    public List<NotificationDto> notifyUser(int recipientId, Integer createdBy, String title, String content) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("r", recipientId).addValue("by", createdBy)
                .addValue("t", truncate(title, 150)).addValue("c", content);
        return jdbc.query("""
                WITH ins AS (
                    INSERT INTO notifications (title, content, created_by, recipient_id)
                    SELECT :t, :c, :by, u.id FROM users u WHERE u.id = :r AND u.is_active
                    RETURNING *
                )
                """ + RETURN_WITH_NAME, p, MAPPER);
    }

    /**
     * Fan-out d'un post : une ligne par destinataire, en un seul INSERT ... SELECT
     * (requete "audience" du guide : users actifs du role/club cible, sauf l'auteur).
     */
    public List<NotificationDto> notifyAudience(int roleId, int clubId, int authorId, String title, String content) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("role", roleId).addValue("club", clubId).addValue("author", authorId)
                .addValue("t", truncate(title, 150)).addValue("c", content);
        return jdbc.query("""
                WITH ins AS (
                    INSERT INTO notifications (title, content, created_by, recipient_id)
                    SELECT :t, :c, :author, u.id FROM users u
                    WHERE u.is_active
                      AND (:role = 0 OR u.role_id = :role)
                      AND (:club = 0 OR u.club_id = :club)
                      AND u.id <> :author
                    RETURNING *
                )
                """ + RETURN_WITH_NAME, p, MAPPER);
    }

    // ---------- Lecture (client) ----------

    public List<NotificationDto> list(int me, Integer beforeId, boolean unseenOnly, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("me", me).addValue("limit", limit).addValue("before", beforeId);
        StringBuilder sql = new StringBuilder("""
                SELECT n.*, (cu.first_name || ' ' || cu.last_name) AS created_by_name
                FROM notifications n LEFT JOIN users cu ON cu.id = n.created_by
                WHERE n.recipient_id = :me
                """);
        if (beforeId != null) sql.append(" AND n.id < :before");
        if (unseenOnly) sql.append(" AND n.seen = FALSE");
        sql.append(" ORDER BY n.created_at DESC, n.id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), p, MAPPER);
    }

    public long unseenCount(int me) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id = :me AND seen = FALSE",
                Map.of("me", me), Long.class);
        return n == null ? 0 : n;
    }

    /** Le filtre recipient_id empeche de marquer la notification de quelqu'un d'autre. */
    public boolean markSeen(int me, int id) {
        return jdbc.update("UPDATE notifications SET seen = TRUE WHERE id = :id AND recipient_id = :me",
                Map.of("id", id, "me", me)) > 0;
    }

    public int markAllSeen(int me) {
        return jdbc.update("UPDATE notifications SET seen = TRUE WHERE recipient_id = :me AND seen = FALSE", Map.of("me", me));
    }

    public boolean delete(int me, int id) {
        return jdbc.update("DELETE FROM notifications WHERE id = :id AND recipient_id = :me", Map.of("id", id, "me", me)) > 0;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
