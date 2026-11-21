package com.sos.club.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Cote "producteur" de l'outbox.
 *
 * publish() DOIT etre appele dans la transaction qui ecrit la donnee (post, like, comment...):
 * l'event et la donnee sont commites ensemble ou pas du tout (MANDATORY le garantit).
 * Apres le commit, OutboxDispatchListener recoit le signal et fait traiter l'event
 * immediatement ; si ca echoue, EventWorker le reprendra en polling.
 */
@Service
public class OutboxService {

    /** Signal Spring interne publie a chaque insertion dans events. */
    public record OutboxEventCreated(long eventId, String eventType) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher publisher;

    public OutboxService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long publish(String eventType, Map<String, ?> payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload non serialisable", e);
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO events (event_type, payload) VALUES (:type, CAST(:payload AS jsonb)) RETURNING id",
                Map.of("type", eventType, "payload", json), Long.class);
        publisher.publishEvent(new OutboxEventCreated(id, eventType));
        return id;
    }
}
