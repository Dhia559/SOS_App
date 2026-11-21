package com.sos.club.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoggingPushSender implements PushSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(List<String> deviceTokens, NotificationDto n) {
        log.debug("[push] -> user {} ({} appareil(s)) : {} - {}", n.recipientId(), deviceTokens.size(), n.title(), n.content());
    }
}
