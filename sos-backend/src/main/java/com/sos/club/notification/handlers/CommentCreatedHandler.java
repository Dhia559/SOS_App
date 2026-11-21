package com.sos.club.notification.handlers;

import com.sos.club.common.Pairs;
import com.sos.club.events.EventHandler;
import com.sos.club.events.EventRow;
import com.sos.club.events.EventTypes;
import com.sos.club.notification.NotificationDto;
import com.sos.club.notification.NotificationService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** comment.created -> notifie l'auteur du post (pas s'il commente son propre post). */
@Component
public class CommentCreatedHandler extends HandlerSupport implements EventHandler {
    private final NotificationService notifications;

    public CommentCreatedHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        super(jdbc);
        this.notifications = notifications;
    }

    @Override
    public String eventType() { return EventTypes.COMMENT_CREATED; }

    record CommentInfo(int userId, String content, int postAuthorId) {}

    @Override
    public List<NotificationDto> handle(EventRow ev) {
        // Commentaire (ou post) supprime : rien a notifier.
        return jdbc.query("""
                        SELECT c.user_id, c.content, p.user_id AS post_author
                        FROM comments c JOIN posts p ON p.id = c.post_id
                        WHERE c.id = :id
                        """, Map.of("id", ev.intField("comment_id")),
                        (rs, i) -> new CommentInfo(rs.getInt("user_id"), rs.getString("content"), rs.getInt("post_author")))
                .stream().findFirst()
                .filter(c -> c.userId() != c.postAuthorId())
                .map(c -> notifications.notifyUser(c.postAuthorId(), c.userId(), "Nouveau commentaire",
                        fullName(c.userId()) + " a commente votre publication : « " + Pairs.excerpt(c.content(), 100) + " »"))
                .orElse(List.of());
    }
}
