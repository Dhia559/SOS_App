package com.sos.club.notification.handlers;

import com.sos.club.events.EventHandler;
import com.sos.club.events.EventRow;
import com.sos.club.events.EventTypes;
import com.sos.club.notification.NotificationDto;
import com.sos.club.notification.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** friend.requested et friend.accepted. */
@Configuration
public class FriendHandlers {

    record FriendshipInfo(int user1, int user2, int requester, String status) {
        int other(int id) { return id == user1 ? user2 : user1; }
    }

    static Optional<FriendshipInfo> load(NamedParameterJdbcTemplate jdbc, int id) {
        return jdbc.query("SELECT user1_id, user2_id, requester_id, status FROM friendships WHERE id = :id",
                Map.of("id", id),
                (rs, i) -> new FriendshipInfo(rs.getInt("user1_id"), rs.getInt("user2_id"),
                        rs.getInt("requester_id"), rs.getString("status")))
                .stream().findFirst();
    }

    /** Demande recue : notifie le destinataire de la demande (si elle est toujours en attente). */
    @Bean
    EventHandler friendRequestedHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        return new Base(jdbc, EventTypes.FRIEND_REQUESTED) {
            @Override
            public List<NotificationDto> handle(EventRow ev) {
                return load(jdbc, ev.intField("friendship_id"))
                        .filter(f -> "pending".equals(f.status()))
                        .map(f -> notifications.notifyUser(f.other(f.requester()), f.requester(), "Demande d'ami",
                                fullName(f.requester()) + " vous a envoye une demande d'ami."))
                        .orElse(List.of());
            }
        };
    }

    /** Demande acceptee : notifie celui qui avait fait la demande. */
    @Bean
    EventHandler friendAcceptedHandler(NamedParameterJdbcTemplate jdbc, NotificationService notifications) {
        return new Base(jdbc, EventTypes.FRIEND_ACCEPTED) {
            @Override
            public List<NotificationDto> handle(EventRow ev) {
                return load(jdbc, ev.intField("friendship_id"))
                        .filter(f -> "accepted".equals(f.status()))
                        .map(f -> {
                            Integer accepter = ev.optIntField("user_id");
                            int acceptedBy = accepter != null ? accepter : f.other(f.requester());
                            int notifyWho = f.other(acceptedBy);
                            return notifications.notifyUser(notifyWho, acceptedBy, "Demande acceptee",
                                    fullName(acceptedBy) + " a accepte votre demande d'ami.");
                        })
                        .orElse(List.of());
            }
        };
    }

    abstract static class Base extends HandlerSupport implements EventHandler {
        private final String type;

        Base(NamedParameterJdbcTemplate jdbc, String type) {
            super(jdbc);
            this.type = type;
        }

        @Override
        public String eventType() { return type; }
    }
}
