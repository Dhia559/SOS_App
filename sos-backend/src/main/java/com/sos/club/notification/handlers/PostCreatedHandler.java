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

/** post.created -> une notification par membre de l'audience du post (sauf l'auteur). */
@Component
public class PostCreatedHandler extends HandlerSupport implements EventHandler {
    private final NotificationService notifications;

    public PostCreatedHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        super(jdbc);
        this.notifications = notifications;
    }

    @Override
    public String eventType() { return EventTypes.POST_CREATED; }

    @Override
    public List<NotificationDto> handle(EventRow ev) {
        // Post supprime entre-temps : rien a notifier, l'event est simplement traite.
        return post(ev.intField("post_id"))
                .map(p -> notifications.notifyAudience(p.roleId(), p.clubId(), p.authorId(),
                        "Nouvelle publication",
                        fullName(p.authorId()) + " a publie : « " + Pairs.excerpt(p.content(), 120) + " »"))
                .orElse(List.of());
    }
}
