package com.sos.club.events;

import com.sos.club.common.ApiException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Endpoints de debug / supervision de la file d'events. */
@RestController
@RequestMapping("/api/internal/events")
public class EventAdminController {
    private final EventRepository events;
    private final EventProcessor processor;

    public EventAdminController(EventRepository events, EventProcessor processor) {
        this.events = events;
        this.processor = processor;
    }

    @GetMapping
    public List<EventRow> list(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "50") int limit) {
        return events.list(status, Math.min(Math.max(limit, 1), 500));
    }

    /** Force le traitement d'un event (remet en file s'il est 'failed'). */
    @PostMapping("/{id}/dispatch")
    public Map<String, Object> dispatch(@PathVariable long id) {
        events.requeue(id);
        boolean done = processor.dispatchNow(id);
        if (!done) throw ApiException.conflict("Event introuvable, deja traite, ou pas encore du (next_retry_at)");
        return Map.of("eventId", id, "dispatched", true);
    }

    /** Lance un tour du worker tout de suite. */
    @PostMapping("/process")
    public Map<String, Object> processNow() {
        return Map.of("processed", processor.processBatch(100));
    }
}
