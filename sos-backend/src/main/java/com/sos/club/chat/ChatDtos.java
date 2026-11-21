package com.sos.club.chat;

import com.sos.club.common.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class ChatDtos {
    private ChatDtos() {}

    public record MessageDto(int id, int conversationId, int senderId, String content, OffsetDateTime createdAt, boolean read) {}

    public record ConversationDto(int id, UserSummary otherUser, MessageDto lastMessage, long unreadCount, OffsetDateTime createdAt) {}

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {}

    /** Payload STOMP pour /app/chat.send */
    public record WsSendMessage(@NotNull Integer conversationId, @NotBlank String content) {}

    /** Evenement Spring interne -> push WebSocket apres commit. */
    public record MessageCreated(MessageDto message, int recipientId) {}

    /** Accuse de lecture pousse a l'expediteur. */
    public record ReadReceipt(int conversationId, int readerId, int count) {}
}
