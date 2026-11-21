package com.sos.club.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Le "listener" : des que la transaction du feed/chat est commitee, on traite l'event
 * tout de suite dans un thread separe (la requete HTTP de l'utilisateur n'attend pas
 * la creation des notifications). Si le commit echoue, rien n'est declenche.
 */
@Component
public class OutboxDispatchListener {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchListener.class);

    private final EventProcessor processor;

    public OutboxDispatchListener(EventProcessor processor) { this.processor = processor; }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCreated(OutboxService.OutboxEventCreated e) {
        try {
            processor.dispatchNow(e.eventId());
        } catch (Exception ex) {
            // Pas grave : l'event reste en base, le worker le reprendra.
            log.warn("Dispatch immediat de l'event {} echoue: {}", e.eventId(), ex.getMessage());
        }
    }
}
