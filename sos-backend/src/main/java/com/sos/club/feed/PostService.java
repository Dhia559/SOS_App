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
            rs.getBoolean("liked_by_me"));

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

        // 1) la donnee
        Integer postId = jdbc.queryForObject("""
                INSERT INTO posts (user_id, content, visible_role_id, visible_club_id)
                VALUES (:me, :content, :role, :club) RETURNING id
                """, Map.of("me", me, "content", r.content().strip(), "role", role, "club", club), Integer.class);

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

    private void checkExists(String table, int id, String msg) {
        Boolean ok = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)", Map.of("id", id), Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw ApiException.badRequest(msg);
    }
}
