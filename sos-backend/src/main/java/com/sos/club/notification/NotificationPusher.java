package com.sos.club.notification;

import com.sos.club.user.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Livraison temps reel des notifications deja enregistrees en base :
 *  - WebSocket : /user/queue/notifications  (+ /user/queue/notifications.count)
 *  - push mobile : PushSender avec les device_tokens du destinataire
 * Best effort : une erreur ici ne remet jamais en cause la notification en base.
 */
@Component
public class NotificationPusher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPusher.class);

    private final SimpMessagingTemplate ws;
    private final DeviceTokenRepository tokens;
    private final PushSender pushSender;
    private final NotificationService notifications;

    public NotificationPusher(SimpMessagingTemplate ws, DeviceTokenRepository tokens,
                              PushSender pushSender, NotificationService notifications) {
        this.ws = ws;
        this.tokens = tokens;
        this.pushSender = pushSender;
        this.notifications = notifications;
    }

    public void push(List<NotificationDto> list) {
        for (NotificationDto n : list) {
            String user = String.valueOf(n.recipientId());
            try {
                ws.convertAndSendToUser(user, "/queue/notifications", n);
                ws.convertAndSendToUser(user, "/queue/notifications.count",
                        Map.of("count", notifications.unseenCount(n.recipientId())));
            } catch (Exception e) {
                log.warn("Push WebSocket echoue pour user {}: {}", user, e.getMessage());
            }
            try {
                List<String> deviceTokens = tokens.tokensOf(n.recipientId());
                if (!deviceTokens.isEmpty()) pushSender.send(deviceTokens, n);
            } catch (Exception e) {
                log.warn("Push mobile echoue pour user {}: {}", user, e.getMessage());
            }
        }
    }
}
