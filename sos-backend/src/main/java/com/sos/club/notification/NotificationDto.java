package com.sos.club.notification;

import java.time.OffsetDateTime;

public record NotificationDto(
        int id,
        String title,
        String content,
        Integer createdBy,
        String createdByName,
        int recipientId,
        boolean seen,
        OffsetDateTime createdAt) {
}
