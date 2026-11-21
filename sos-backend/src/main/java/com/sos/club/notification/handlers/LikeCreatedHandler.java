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

/** like.created -> notifie l'auteur du post (pas s'il aime son propre post). */
@Component
public class LikeCreatedHandler extends HandlerSupport implements EventHandler {
    private final NotificationService notifications;

    public LikeCreatedHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        super(jdbc);
        this.notifications = notifications;
    }

    @Override
    public String eventType() { return EventTypes.LIKE_CREATED; }

    @Override
    public List<NotificationDto> handle(EventRow ev) {
        int likerId = ev.intField("user_id");
        return post(ev.intField("post_id"))
                .filter(p -> p.authorId() != likerId)
                .map(p -> notifications.notifyUser(p.authorId(), likerId, "Nouveau j'aime",
                        fullName(likerId) + " a aime votre publication : « " + Pairs.excerpt(p.content(), 80) + " »"))
                .orElse(List.of());
    }
}
