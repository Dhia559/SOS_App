package com.sos.club.user;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    public record Credentials(int id, String email, String passwordHash) {}

    static final String SELECT = """
            SELECT u.id, u.first_name, u.last_name, u.email, u.birthday, u.sex,
                   u.role_id, r.name AS role_name, u.club_id, c.name AS club_name, u.is_active
            FROM users u
            LEFT JOIN roles r ON r.id = u.role_id
            LEFT JOIN clubs c ON c.id = u.club_id
            """;

    static final RowMapper<UserDto> MAPPER = (rs, i) -> new UserDto(
            rs.getInt("id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getObject("birthday", LocalDate.class),
            rs.getString("sex"),
            rs.getObject("role_id", Integer.class),
            rs.getString("role_name"),
            rs.getObject("club_id", Integer.class),
            rs.getString("club_name"),
            rs.getBoolean("is_active"));

    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserDto> findById(int id) {
        return jdbc.query(SELECT + " WHERE u.id = :id", Map.of("id", id), MAPPER).stream().findFirst();
    }

    /** Requete de login du guide : utilise l'index uq_users_email_lower. */
    public Optional<Credentials> findActiveCredentials(String email) {
        return jdbc.query("""
                SELECT id, email, password_hash FROM users
                WHERE LOWER(email) = LOWER(:email) AND is_active
                """, Map.of("email", email),
                (rs, i) -> new Credentials(rs.getInt("id"), rs.getString("email"), rs.getString("password_hash")))
                .stream().findFirst();
    }

    public boolean emailExists(String email) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(:email))",
                Map.of("email", email), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    public int insert(String firstName, String lastName, String email, String passwordHash,
                      LocalDate birthday, String sex, Integer roleId, Integer clubId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fn", firstName).addValue("ln", lastName).addValue("email", email)
                .addValue("hash", passwordHash).addValue("birthday", birthday).addValue("sex", sex)
                .addValue("role", roleId).addValue("club", clubId);
        Integer id = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, birthday, sex, role_id, club_id)
                VALUES (:fn, :ln, :email, :hash, :birthday, :sex, :role, :club)
                RETURNING id
                """, p, Integer.class);
        return id;
    }

    public void updateProfile(int id, String firstName, String lastName, LocalDate birthday, String sex) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id).addValue("fn", firstName).addValue("ln", lastName)
                .addValue("birthday", birthday).addValue("sex", sex);
        jdbc.update("""
                UPDATE users SET first_name = COALESCE(:fn, first_name),
                                 last_name  = COALESCE(:ln, last_name),
                                 birthday   = COALESCE(:birthday, birthday),
                                 sex        = COALESCE(:sex, sex)
                WHERE id = :id
                """, p);
    }

    public List<UserDto> search(String q, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("q", "%" + (q == null ? "" : q.trim().toLowerCase()) + "%")
                .addValue("limit", limit);
        return jdbc.query(SELECT + """
                 WHERE u.is_active
                   AND (LOWER(u.first_name || ' ' || u.last_name) LIKE :q OR LOWER(u.email) LIKE :q)
                 ORDER BY u.first_name, u.last_name
                 LIMIT :limit
                """, p, MAPPER);
    }

    public void updatePasswordHash(int id, String hash) {
        jdbc.update("UPDATE users SET password_hash = :h WHERE id = :id", Map.of("h", hash, "id", id));
    }

    public int updateAllPasswordHashes(String hash) {
        return jdbc.update("UPDATE users SET password_hash = :h", Map.of("h", hash));
    }

    public boolean roleExists(int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM roles WHERE id = :id)", Map.of("id", id), Boolean.class));
    }

    public boolean clubExists(int id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM clubs WHERE id = :id)", Map.of("id", id), Boolean.class));
    }
}
