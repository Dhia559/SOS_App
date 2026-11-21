package com.sos.club.notification;

import java.util.List;

/**
 * Envoi d'une notification push vers les appareils (tokens de device_tokens).
 * Implementation par defaut : LoggingPushSender (log seulement).
 * Pour du vrai push, cree un bean FcmPushSender implements PushSender annote @Primary.
 */
public interface PushSender {
    void send(List<String> deviceTokens, NotificationDto notification);
}
