package com.sos.club.notification.handlers;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.Optional;

/** Petites lectures communes aux handlers. */
abstract class HandlerSupport {
    protected final NamedParameterJdbcTemplate jdbc;

    protected HandlerSupport(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    protected String fullName(int userId) {
        return jdbc.query("SELECT first_name || ' ' || last_name AS n FROM users WHERE id = :id",
                        Map.of("id", userId), (rs, i) -> rs.getString("n"))
                .stream().findFirst().orElse("Quelqu'un");
    }

    record PostInfo(int id, int authorId, String content, int roleId, int clubId) {}

    protected Optional<PostInfo> post(int postId) {
        return jdbc.query("SELECT id, user_id, content, visible_role_id, visible_club_id FROM posts WHERE id = :id",
                Map.of("id", postId),
                (rs, i) -> new PostInfo(rs.getInt("id"), rs.getInt("user_id"), rs.getString("content"),
                        rs.getInt("visible_role_id"), rs.getInt("visible_club_id")))
                .stream().findFirst();
    }
}
