package com.sos.club.events;

import com.sos.club.notification.NotificationDto;

import java.util.List;

/**
 * Un handler par event_type. handle() est appele DANS la transaction qui marque
 * l'event 'processed' : il insere les notifications et les retourne (pour le push
 * temps reel apres commit). En cas d'exception tout est annule et l'event est
 * replanifie avec backoff -> pas de notifications en double.
 */
public interface EventHandler {
    String eventType();

    List<NotificationDto> handle(EventRow event);
}
