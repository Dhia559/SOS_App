package com.sos.club.chat;

import com.sos.club.chat.ChatDtos.*;
import com.sos.club.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    /** Boite de reception : dernier message + nb de non-lus par conversation. */
    @GetMapping
    public List<ConversationDto> inbox(@AuthenticationPrincipal AuthUser me) {
        return chat.inbox(me.id());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser me) {
        return Map.of("count", chat.unreadTotal(me.id()));
    }

    /** Ouvre (ou retrouve) la conversation avec un ami. */
    @PostMapping("/with/{userId}")
    public ConversationDto open(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        return chat.open(me.id(), userId);
    }

    @GetMapping("/{id}")
    public ConversationDto get(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return chat.conversation(me.id(), id);
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@AuthenticationPrincipal AuthUser me, @PathVariable int id,
                                     @RequestParam(required = false) Integer before,
                                     @RequestParam(defaultValue = "50") int limit) {
        return chat.messages(me.id(), id, before, limit);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@AuthenticationPrincipal AuthUser me, @PathVariable int id,
                           @Validated @RequestBody SendMessageRequest r) {
        return chat.send(me.id(), id, r.content());
    }

    /** Modifier son propre message. */
    @PutMapping("/{id}/messages/{messageId}")
    public MessageDto edit(@AuthenticationPrincipal AuthUser me, @PathVariable int id, @PathVariable int messageId,
                           @Validated @RequestBody EditMessageRequest r) {
        return chat.edit(me.id(), id, messageId, r.content());
    }

    /** Supprimer son propre message. */
    @DeleteMapping("/{id}/messages/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable int id, @PathVariable int messageId) {
        chat.delete(me.id(), id, messageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public Map<String, Integer> markRead(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return Map.of("updated", chat.markRead(me.id(), id));
    }
}
