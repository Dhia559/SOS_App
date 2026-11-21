package com.sos.club.social;

import com.sos.club.security.AuthUser;
import com.sos.club.social.FriendService.FriendshipDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friends;

    public FriendController(FriendService friends) { this.friends = friends; }

    @GetMapping
    public List<FriendshipDto> list(@AuthenticationPrincipal AuthUser me) {
        return friends.friends(me.id());
    }

    @GetMapping("/requests")
    public List<FriendshipDto> incoming(@AuthenticationPrincipal AuthUser me) {
        return friends.incoming(me.id());
    }

    @GetMapping("/requests/sent")
    public List<FriendshipDto> outgoing(@AuthenticationPrincipal AuthUser me) {
        return friends.outgoing(me.id());
    }

    @GetMapping("/{userId}")
    public FriendshipDto status(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        return friends.status(me.id(), userId);
    }

    /** Envoyer une demande (ou accepter si l'autre a deja demande). */
    @PostMapping("/{userId}")
    public FriendshipDto request(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        return friends.request(me.id(), userId);
    }

    @PostMapping("/{userId}/accept")
    public FriendshipDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        return friends.accept(me.id(), userId);
    }

    /** Refuser / annuler / retirer un ami. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        friends.remove(me.id(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/block")
    public FriendshipDto block(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        return friends.block(me.id(), userId);
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal AuthUser me, @PathVariable int userId) {
        friends.unblock(me.id(), userId);
        return ResponseEntity.noContent().build();
    }
}
