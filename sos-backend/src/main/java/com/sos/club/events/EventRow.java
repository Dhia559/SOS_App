package com.sos.club.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record EventRow(
        long id,
        String eventType,
        JsonNode payload,
        String status,
        int attempts,
        OffsetDateTime createdAt,
        OffsetDateTime lockedAt,
        OffsetDateTime processedAt,
        OffsetDateTime nextRetryAt,
        String lastError) {

    public int intField(String name) {
        JsonNode n = payload.get(name);
        if (n == null || !n.canConvertToInt()) throw new IllegalArgumentException("payload." + name + " manquant");
        return n.asInt();
    }

    public Integer optIntField(String name) {
        JsonNode n = payload.get(name);
        return (n == null || n.isNull()) ? null : n.asInt();
    }
}
