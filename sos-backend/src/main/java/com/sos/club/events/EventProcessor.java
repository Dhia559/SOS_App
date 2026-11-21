package com.sos.club.events;

import com.sos.club.notification.NotificationDto;
import com.sos.club.notification.NotificationPusher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cote "consommateur" de l'outbox : transforme un event en notifications.
 *
 * Etapes 3-5 du guide :
 *  - dans UNE transaction : handler (insere les notifications) + markProcessed
 *  - si erreur : rollback complet puis markFailed (backoff / failed apres 5 essais)
 *  - apres commit : push temps reel (WebSocket + push mobile)
 */
@Service
public class EventProcessor {
    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private final EventRepository events;
    private final Map<String, EventHandler> handlers;
    private final TransactionTemplate tx;
    private final NotificationPusher pusher;
    private final int maxAttempts;

    public EventProcessor(EventRepository events, List<EventHandler> handlers, TransactionTemplate tx,
                          NotificationPusher pusher, @Value("${app.events.max-attempts}") int maxAttempts) {
        this.events = events;
        this.handlers = handlers.stream().collect(Collectors.toMap(EventHandler::eventType, Function.identity()));
        this.tx = tx;
        this.pusher = pusher;
        this.maxAttempts = maxAttempts;
        log.info("Handlers d'events enregistres: {}", this.handlers.keySet());
    }

    /** Traitement immediat d'un event qui vient d'etre commite. */
    public boolean dispatchNow(long eventId) {
        List<EventRow> claimed = events.claimOne(eventId);
        claimed.forEach(this::process);
        return !claimed.isEmpty();
    }

    /** Traitement d'un lot (appele par le worker en polling). */
    public int processBatch(int batchSize) {
        List<EventRow> claimed = events.claimBatch(batchSize);
        claimed.forEach(this::process);
        return claimed.size();
    }

    private void process(EventRow ev) {
        List<NotificationDto> created;
        try {
            created = tx.execute(status -> {
                EventHandler h = handlers.get(ev.eventType());
                if (h == null) throw new IllegalStateException("Aucun handler pour le type '" + ev.eventType() + "'");
                List<NotificationDto> result = h.handle(ev);
                if (events.markProcessed(ev.id()) == 0) {
                    // Un reaper nous a pris l'event : on annule pour eviter les doublons.
                    throw new IllegalStateException("Event " + ev.id() + " n'est plus en 'processing'");
                }
                return result;
            });
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Event {} ({}) en echec (tentative {}): {}", ev.id(), ev.eventType(), ev.attempts() + 1, msg);
            try {
                events.markFailed(ev.id(), msg.length() > 2000 ? msg.substring(0, 2000) : msg, maxAttempts);
            } catch (Exception e2) {
                log.error("Impossible d'enregistrer l'echec de l'event {}", ev.id(), e2);
            }
            return;
        }
        log.debug("Event {} ({}) traite: {} notification(s)", ev.id(), ev.eventType(), created == null ? 0 : created.size());
        if (created != null && !created.isEmpty()) pusher.push(created);
    }
}
