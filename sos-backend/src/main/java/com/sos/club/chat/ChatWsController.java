package com.sos.club.chat;

import com.sos.club.chat.ChatDtos.WsSendMessage;
import com.sos.club.security.AuthUser;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/** Envoi de messages directement par WebSocket : SEND /app/chat.send {conversationId, content}. */
@Controller
public class ChatWsController {
    private final ChatService chat;

    public ChatWsController(ChatService chat) { this.chat = chat; }

    @MessageMapping("/chat.send")
    public void send(@Payload WsSendMessage msg, Principal principal) {
        chat.send(userId(principal), msg.conversationId(), msg.content());
        // Le message est pousse aux deux users par ChatPushListener apres commit.
    }

    @MessageMapping("/chat.read")
    public void read(@Payload Map<String, Integer> body, Principal principal) {
        chat.markRead(userId(principal), body.get("conversationId"));
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> error(Exception e) {
        return Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private static int userId(Principal p) {
        if (p instanceof Authentication a && a.getPrincipal() instanceof AuthUser u) return u.id();
        throw new IllegalStateException("Non authentifie");
    }
}
