package com.sos.club.feed;

import com.sos.club.common.ApiException;
import com.sos.club.common.Roles;
import com.sos.club.common.UserSummary;
import com.sos.club.events.EventTypes;
import com.sos.club.events.OutboxService;
import com.sos.club.feed.FeedDtos.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Feed, posts, likes, commentaires.
 * Chaque ecriture enregistre d'abord la donnee puis l'event dans la MEME transaction
 * (outbox). Les notifications sont produites apres commit par le systeme d'events.
 */
@Service
public class PostService {
    private final NamedParameterJdbcTemplate jdbc;
    private final OutboxService outbox;

    public PostService(NamedParameterJdbcTemplate jdbc, OutboxService outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    // Visibilite (guide section 03) : role du post = 0 ou celui du viewer, ET club = 0 ou celui du viewer.
    // Les roles d'association appartiennent a tous les clubs. L'auteur voit toujours ses propres posts.
    private static final String VISIBLE = """
            ((p.visible_role_id IN (0, viewer.role_id)
              AND (p.visible_club_id IN (0, viewer.club_id) OR viewer.role_id IN (%s)))
             OR p.user_id = viewer.id)
            """.formatted(Roles.ASSOCIATION_IDS_SQL);

    private static final String POST_SELECT = """
            SELECT p.id, p.content, p.visible_role_id, p.visible_club_id, p.created_at,
                   p.event_title, p.event_starts_at, p.event_place,
                   EXISTS (SELECT 1 FROM post_images pi WHERE pi.post_id = p.id) AS has_image,
                   vr.name AS visible_role_name, vc.name AS visible_club_name,
                   a.id AS a_id, a.first_name AS a_first_name, a.last_name AS a_last_name,
                   ar.name AS a_role_name, ac.name AS a_club_name,
                   (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)    AS like_count,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comment_count,
                   EXISTS (SELECT 1 FROM likes l WHERE l.post_id = p.id AND l.user_id = viewer.id) AS liked_by_me
            FROM posts p
            JOIN users viewer ON viewer.id = :me
            JOIN users a ON a.id = p.user_id
            LEFT JOIN roles ar ON ar.id = a.role_id
            LEFT JOIN clubs ac ON ac.id = a.club_id
            JOIN roles vr ON vr.id = p.visible_role_id
            JOIN clubs vc ON vc.id = p.visible_club_id
            """;

    private static final RowMapper<PostDto> POST_MAPPER = (rs, i) -> new PostDto(
            rs.getInt("id"),
            rs.getString("content"),
            UserSummary.from(rs, "a_"),
            rs.getInt("visible_role_id"),
            rs.getString("visible_role_name"),
            rs.getInt("visible_club_id"),
            rs.getString("visible_club_name"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getLong("like_count"),
            rs.getLong("comment_count"),
            rs.getBoolean("liked_by_me"),
            rs.getBoolean("has_image"),
            rs.getString("event_title") == null ? null : new PostEvent(
                    rs.getString("event_title"),
                    rs.getObject("event_starts_at", OffsetDateTime.class),
                    rs.getString("event_place")));

    /** Photo d'un post : type MIME + octets. */
    public record PostImage(String contentType, byte[] data) {}

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final java.util.regex.Pattern DATA_URL =
            java.util.regex.Pattern.compile("^data:(image/(?:jpeg|png|webp|gif));base64,(.+)$", java.util.regex.Pattern.DOTALL);

    private static final String COMMENT_SELECT = """
            SELECT c.id, c.post_id, c.content, c.created_at,
                   a.id AS a_id, a.first_name AS a_first_name, a.last_name AS a_last_name,
                   ar.name AS a_role_name, ac.name AS a_club_name
            FROM comments c
            JOIN users a ON a.id = c.user_id
            LEFT JOIN roles ar ON ar.id = a.role_id
            LEFT JOIN clubs ac ON ac.id = a.club_id
            """;

    private static final RowMapper<CommentDto> COMMENT_MAPPER = (rs, i) -> new CommentDto(
            rs.getInt("id"), rs.getInt("post_id"), UserSummary.from(rs, "a_"),
            rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class));

    // ------------------------------------------------------------------ feed

    /** Query 5 du guide : feed cible, plus recent d'abord, pagination keyset. */
    public FeedPage feed(int me, OffsetDateTime before, int limit) {
        return page(me, null, before, limit);
    }

    public FeedPage postsOfAuthor(int me, int authorId, OffsetDateTime before, int limit) {
        return page(me, authorId, before, limit);
    }

    private FeedPage page(int me, Integer authorId, OffsetDateTime before, int limit) {
        int lim = Math.min(Math.max(limit, 1), 50);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("me", me).addValue("limit", lim).addValue("before", before).addValue("author", authorId);
        StringBuilder sql = new StringBuilder(POST_SELECT).append(" WHERE ").append(VISIBLE);
        if (authorId != null) sql.append(" AND p.user_id = :author");
        if (before != null) sql.append(" AND p.created_at < :before");
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit");
        List<PostDto> posts = jdbc.query(sql.toString(), p, POST_MAPPER);
        OffsetDateTime next = posts.size() == lim ? posts.get(posts.size() - 1).createdAt() : null;
        return new FeedPage(posts, next);
    }

    public PostDto get(int me, int postId) {
        return jdbc.query(POST_SELECT + " WHERE p.id = :id AND " + VISIBLE,
                        new MapSqlParameterSource().addValue("me", me).addValue("id", postId), POST_MAPPER)
                .stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Publication introuvable"));
    }

    private void ensureVisible(int me, int postId) {
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM posts p JOIN users viewer ON viewer.id = :me WHERE p.id = :id AND "
                + VISIBLE + ")", Map.of("me", me, "id", postId), Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw ApiException.notFound("Publication introuvable");
    }

    // ------------------------------------------------------------------ posts

    @Transactional
    public PostDto create(int me, CreatePostRequest r) {
        int role = r.visibleRoleId() == null ? 0 : r.visibleRoleId();
        int club = r.visibleClubId() == null ? 0 : r.visibleClubId();
        checkExists("roles", role, "Role cible inconnu");
        checkExists("clubs", club, "Club cible inconnu");

        // Evenement : titre + date obligatoires ensemble, lieu optionnel
        String eventTitle = blankToNull(r.eventTitle());
        String eventPlace = blankToNull(r.eventPlace());
        if ((eventTitle == null) != (r.eventStartsAt() == null)) {
            throw ApiException.badRequest("Un evenement doit avoir un titre et une date");
        }
        if (eventTitle == null && eventPlace != null) throw ApiException.badRequest("Lieu sans evenement");
        PostImage image = decodeImage(r.image());

        // 1) la donnee
        Integer postId = jdbc.queryForObject("""
                INSERT INTO posts (user_id, content, visible_role_id, visible_club_id, event_title, event_starts_at, event_place)
                VALUES (:me, :content, :role, :club, :et, :es, :ep) RETURNING id
                """, new MapSqlParameterSource()
                        .addValue("me", me).addValue("content", r.content().strip())
                        .addValue("role", role).addValue("club", club)
                        .addValue("et", eventTitle).addValue("es", r.eventStartsAt()).addValue("ep", eventPlace),
                Integer.class);
        if (image != null) {
            jdbc.update("INSERT INTO post_images (post_id, content_type, data) VALUES (:p, :t, :d)",
                    new MapSqlParameterSource().addValue("p", postId).addValue("t", image.contentType()).addValue("d", image.data()));
        }

        // 2) l'event, meme transaction
        outbox.publish(EventTypes.POST_CREATED, Map.of("post_id", postId, "user_id", me));
        return get(me, postId);
    }

    @Transactional
    public PostDto update(int me, int postId, UpdatePostRequest r) {
        int n = jdbc.update("UPDATE posts SET content = :c WHERE id = :id AND user_id = :me",
                Map.of("c", r.content().strip(), "id", postId, "me", me));
        if (n == 0) throw ApiException.forbidden("Vous ne pouvez modifier que vos publications");
        return get(me, postId);
    }

    @Transactional
    public void delete(int me, int postId) {
        int n = jdbc.update("DELETE FROM posts WHERE id = :id AND user_id = :me", Map.of("id", postId, "me", me));
        if (n == 0) throw ApiException.forbidden("Publication introuvable ou pas a vous");
    }

    // ------------------------------------------------------------------ likes

    @Transactional
    public LikeState like(int me, int postId) {
        ensureVisible(me, postId);
        int inserted = jdbc.update("INSERT INTO likes (user_id, post_id) VALUES (:me, :p) ON CONFLICT DO NOTHING",
                Map.of("me", me, "p", postId));
        if (inserted == 1) { // pas d'event si le like existait deja
            outbox.publish(EventTypes.LIKE_CREATED, Map.of("post_id", postId, "user_id", me));
        }
        return likeState(me, postId);
    }

    @Transactional
    public LikeState unlike(int me, int postId) {
        ensureVisible(me, postId);
        jdbc.update("DELETE FROM likes WHERE user_id = :me AND post_id = :p", Map.of("me", me, "p", postId));
        return likeState(me, postId);
    }

    public List<LikerDto> likers(int me, int postId) {
        ensureVisible(me, postId);
        return jdbc.query("""
                SELECT l.created_at, a.id AS a_id, a.first_name AS a_first_name, a.last_name AS a_last_name,
                       ar.name AS a_role_name, ac.name AS a_club_name
                FROM likes l JOIN users a ON a.id = l.user_id
                LEFT JOIN roles ar ON ar.id = a.role_id LEFT JOIN clubs ac ON ac.id = a.club_id
                WHERE l.post_id = :p ORDER BY l.created_at DESC
                """, Map.of("p", postId),
                (rs, i) -> new LikerDto(UserSummary.from(rs, "a_"), rs.getObject("created_at", OffsetDateTime.class)));
    }

    private LikeState likeState(int me, int postId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS n, COALESCE(BOOL_OR(user_id = :me), FALSE) AS liked
                FROM likes WHERE post_id = :p
                """, Map.of("me", me, "p", postId),
                (rs, i) -> new LikeState(postId, rs.getBoolean("liked"), rs.getLong("n")));
    }

    // ------------------------------------------------------------------ comments

    public List<CommentDto> comments(int me, int postId) {
        ensureVisible(me, postId);
        return jdbc.query(COMMENT_SELECT + " WHERE c.post_id = :p ORDER BY c.created_at, c.id",
                Map.of("p", postId), COMMENT_MAPPER);
    }

    @Transactional
    public CommentDto addComment(int me, int postId, CreateCommentRequest r) {
        ensureVisible(me, postId);
        Integer commentId = jdbc.queryForObject("""
                INSERT INTO comments (post_id, user_id, content) VALUES (:p, :me, :c) RETURNING id
                """, Map.of("p", postId, "me", me, "c", r.content().strip()), Integer.class);
        outbox.publish(EventTypes.COMMENT_CREATED, Map.of("comment_id", commentId, "post_id", postId, "user_id", me));
        return jdbc.queryForObject(COMMENT_SELECT + " WHERE c.id = :id", Map.of("id", commentId), COMMENT_MAPPER);
    }

    /** L'auteur du commentaire ou l'auteur du post peut le supprimer. */
    @Transactional
    public void deleteComment(int me, int commentId) {
        int n = jdbc.update("""
                DELETE FROM comments c USING posts p
                WHERE c.id = :id AND p.id = c.post_id AND (c.user_id = :me OR p.user_id = :me)
                """, Map.of("id", commentId, "me", me));
        if (n == 0) throw ApiException.forbidden("Commentaire introuvable ou pas a vous");
    }

    // ------------------------------------------------------------------ photo & evenements

    /** Photo d'un post visible par "me" (404 sinon). */
    public PostImage image(int me, int postId) {
        ensureVisible(me, postId);
        return jdbc.query("SELECT content_type, data FROM post_images WHERE post_id = :p", Map.of("p", postId),
                        (rs, i) -> new PostImage(rs.getString("content_type"), rs.getBytes("data")))
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Pas de photo pour cette publication"));
    }

    /** Evenements a venir dans les posts visibles par "me" (panneau "A venir"). */
    public List<UpcomingEvent> upcomingEvents(int me, int limit) {
        return jdbc.query(POST_SELECT + " WHERE " + VISIBLE + """
                         AND p.event_starts_at >= NOW()
                        ORDER BY p.event_starts_at, p.id LIMIT :limit
                        """,
                new MapSqlParameterSource().addValue("me", me).addValue("limit", Math.min(Math.max(limit, 1), 20)),
                (rs, i) -> new UpcomingEvent(rs.getInt("id"), rs.getString("event_title"),
                        rs.getObject("event_starts_at", OffsetDateTime.class), rs.getString("event_place"),
                        UserSummary.from(rs, "a_")));
    }

    private static PostImage decodeImage(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        var m = DATA_URL.matcher(dataUrl);
        if (!m.matches()) throw ApiException.badRequest("Image invalide (JPEG, PNG, WebP ou GIF attendu)");
        byte[] bytes;
        try {
            bytes = java.util.Base64.getMimeDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Image invalide");
        }
        if (bytes.length > MAX_IMAGE_BYTES) throw ApiException.badRequest("Image trop lourde (5 Mo max)");
        return new PostImage(m.group(1), bytes);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private void checkExists(String table, int id, String msg) {
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)", Map.of("id", id), Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw ApiException.badRequest(msg);
    }
}
