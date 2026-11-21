package com.sos.club.notification;

import com.sos.club.common.ApiException;
import com.sos.club.security.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    /** Pagination : passer ?before=<id de la derniere notification recue>. */
    @GetMapping
    public List<NotificationDto> list(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) Integer before,
                                      @RequestParam(defaultValue = "false") boolean unseenOnly,
                                      @RequestParam(defaultValue = "20") int limit) {
        return notifications.list(me.id(), before, unseenOnly, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/unseen-count")
    public Map<String, Long> unseenCount(@AuthenticationPrincipal AuthUser me) {
        return Map.of("count", notifications.unseenCount(me.id()));
    }

    @PutMapping("/{id}/seen")
    public Map<String, Object> markSeen(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        if (!notifications.markSeen(me.id(), id)) throw ApiException.notFound("Notification introuvable");
        return Map.of("id", id, "seen", true);
    }

    @PutMapping("/seen-all")
    public Map<String, Object> markAllSeen(@AuthenticationPrincipal AuthUser me) {
        return Map.of("updated", notifications.markAllSeen(me.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        if (!notifications.delete(me.id(), id)) throw ApiException.notFound("Notification introuvable");
        return ResponseEntity.noContent().build();
    }
}
