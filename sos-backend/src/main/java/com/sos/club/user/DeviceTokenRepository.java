package com.sos.club.user;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Tokens push (FCM / APNs / Web Push) des appareils d'un user : table device_tokens. */
@Repository
public class DeviceTokenRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DeviceTokenRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Un token appartient a un seul appareil : s'il change de compte, on le reattribue. */
    public void upsert(int userId, String token) {
        jdbc.update("""
                INSERT INTO device_tokens (user_id, token) VALUES (:u, :t)
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, updated_at = CURRENT_TIMESTAMP
                """, Map.of("u", userId, "t", token));
    }

    public int delete(int userId, String token) {
        return jdbc.update("DELETE FROM device_tokens WHERE user_id = :u AND token = :t", Map.of("u", userId, "t", token));
    }

    public List<String> tokensOf(int userId) {
        return jdbc.queryForList("SELECT token FROM device_tokens WHERE user_id = :u", Map.of("u", userId), String.class);
    }

    public int deleteToken(String token) {
        return jdbc.update("DELETE FROM device_tokens WHERE token = :t", Map.of("t", token));
    }
}
