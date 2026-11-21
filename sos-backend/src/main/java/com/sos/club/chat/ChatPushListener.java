package com.sos.club.chat;

import com.sos.club.chat.ChatDtos.MessageCreated;
import com.sos.club.chat.ChatDtos.MessageDeleted;
import com.sos.club.chat.ChatDtos.MessageUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Push temps reel du chat, uniquement apres commit :
 *  - /user/queue/messages : le message (envoye aux 2 participants, pour synchroniser plusieurs onglets)
 *  - /user/queue/read     : accuse de lecture pour l'expediteur
 */
@Component
public class ChatPushListener {
    private static final Logger log = LoggerFactory.getLogger(ChatPushListener.class);

    private final SimpMessagingTemplate ws;

    public ChatPushListener(SimpMessagingTemplate ws) { this.ws = ws; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessage(MessageCreated e) {
        try {
            ws.convertAndSendToUser(String.valueOf(e.recipientId()), "/queue/messages", e.message());
            ws.convertAndSendToUser(String.valueOf(e.message().senderId()), "/queue/messages", e.message());
        } catch (Exception ex) {
            log.warn("Push WS du message {} echoue: {}", e.message().id(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpdated(MessageUpdated e) {
        try {
            ws.convertAndSendToUser(String.valueOf(e.recipientId()), "/queue/messages.updated", e.message());
            ws.convertAndSendToUser(String.valueOf(e.message().senderId()), "/queue/messages.updated", e.message());
        } catch (Exception ex) {
            log.warn("Push WS modification du message {} echoue: {}", e.message().id(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeleted(MessageDeleted e) {
        Map<String, Integer> body = Map.of("conversationId", e.conversationId(), "messageId", e.messageId());
        try {
            ws.convertAndSendToUser(String.valueOf(e.recipientId()), "/queue/messages.deleted", body);
            ws.convertAndSendToUser(String.valueOf(e.senderId()), "/queue/messages.deleted", body);
        } catch (Exception ex) {
            log.warn("Push WS suppression du message {} echoue: {}", e.messageId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRead(ChatService.ReadReceiptEvent e) {
        try {
            ws.convertAndSendToUser(String.valueOf(e.notifyUserId()), "/queue/read", e.receipt());
        } catch (Exception ex) {
            log.warn("Push WS accuse de lecture echoue: {}", ex.getMessage());
        }
    }
}
