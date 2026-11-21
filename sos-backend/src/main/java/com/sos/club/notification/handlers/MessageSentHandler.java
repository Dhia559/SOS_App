package com.sos.club.notification.handlers;

import com.sos.club.common.Pairs;
import com.sos.club.events.EventHandler;
import com.sos.club.events.EventRow;
import com.sos.club.events.EventTypes;
import com.sos.club.notification.NotificationDto;
import com.sos.club.notification.NotificationService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** message.sent -> notifie l'autre participant de la conversation. */
@Component
public class MessageSentHandler extends HandlerSupport implements EventHandler {
    private final NotificationService notifications;

    public MessageSentHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        super(jdbc);
        this.notifications = notifications;
    }

    @Override
    public String eventType() { return EventTypes.MESSAGE_SENT; }

    @Override
    public List<NotificationDto> handle(EventRow ev) {
        int convId = ev.intField("conversation_id");
        int senderId = ev.intField("sender_id");
        Integer messageId = ev.optIntField("message_id");

        var conv = jdbc.query("SELECT user1_id, user2_id FROM conversations WHERE id = :id", Map.of("id", convId),
                (rs, i) -> new int[]{rs.getInt("user1_id"), rs.getInt("user2_id")}).stream().findFirst();
        if (conv.isEmpty()) return List.of();
        int[] pair = conv.get();
        if (senderId != pair[0] && senderId != pair[1]) return List.of();
        int recipient = senderId == pair[0] ? pair[1] : pair[0];

        // Les events du seed n'ont pas message_id : on prend alors le dernier message de l'expediteur.
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("c", convId).addValue("s", senderId).addValue("m", messageId);
        String sql = messageId != null
                ? "SELECT content FROM messages WHERE id = :m AND conversation_id = :c"
                : "SELECT content FROM messages WHERE conversation_id = :c AND sender_id = :s ORDER BY created_at DESC, id DESC LIMIT 1";
        String content = jdbc.query(sql, p, (rs, i) -> rs.getString("content")).stream().findFirst().orElse(null);
        if (content == null) return List.of();

        return notifications.notifyUser(recipient, senderId, "Nouveau message",
                fullName(senderId) + " : « " + Pairs.excerpt(content, 100) + " »");
    }
}
