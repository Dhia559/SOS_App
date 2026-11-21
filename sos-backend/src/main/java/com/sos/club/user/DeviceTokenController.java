package com.sos.club.user;

import com.sos.club.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceTokenController {
    private final DeviceTokenRepository tokens;

    public DeviceTokenController(DeviceTokenRepository tokens) { this.tokens = tokens; }

    public record DeviceTokenRequest(@NotBlank String token) {}

    /** A appeler apres login sur mobile / navigateur pour recevoir les push. */
    @PostMapping
    public Map<String, Object> register(@AuthenticationPrincipal AuthUser me, @Validated @RequestBody DeviceTokenRequest r) {
        tokens.upsert(me.id(), r.token().trim());
        return Map.of("registered", true);
    }

    /** A appeler au logout. */
    @DeleteMapping
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal AuthUser me, @RequestParam String token) {
        tokens.delete(me.id(), token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<String> mine(@AuthenticationPrincipal AuthUser me) {
        return tokens.tokensOf(me.id());
    }
}
