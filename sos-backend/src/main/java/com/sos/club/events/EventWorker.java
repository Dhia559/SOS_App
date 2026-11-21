package com.sos.club.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Filet de securite de l'outbox : reprend les events non traites (dispatch immediat
 * rate, retries en backoff, events du seed...), sauve les events bloques, purge.
 */
@Component
public class EventWorker {
    private static final Logger log = LoggerFactory.getLogger(EventWorker.class);

    private final EventProcessor processor;
    private final EventRepository events;
    private final int batchSize;
    private long lastDbErrorLog = 0;

    public EventWorker(EventProcessor processor, EventRepository events,
                       @Value("${app.events.batch-size}") int batchSize) {
        this.processor = processor;
        this.events = events;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.events.poll-interval-ms}", initialDelay = 3000)
    public void poll() {
        try {
            int n;
            do {
                n = processor.processBatch(batchSize);
                if (n > 0) log.info("Worker: {} event(s) traite(s)", n);
            } while (n == batchSize);
        } catch (Exception e) {
            logDbError(e);
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void reaper() {
        try {
            int n = events.reap();
            if (n > 0) log.warn("Reaper: {} event(s) bloque(s) remis en 'pending'", n);
        } catch (Exception e) {
            logDbError(e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purge() {
        try {
            int n = events.purgeProcessedOlderThanDays(7);
            if (n > 0) log.info("Purge: {} event(s) traite(s) supprime(s)", n);
        } catch (Exception e) {
            logDbError(e);
        }
    }

    private void logDbError(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastDbErrorLog > 30_000) { // evite de spammer les logs si la BD est down
            lastDbErrorLog = now;
            log.error("Worker d'events: {}", e.getMessage());
        }
    }
}
