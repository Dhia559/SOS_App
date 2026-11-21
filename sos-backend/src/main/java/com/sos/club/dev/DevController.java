package com.sos.club.dev;

import com.sos.club.common.ApiException;
import com.sos.club.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Outils de dev (desactivables avec DEV_ENDPOINTS=false).
 * Le seed contient 'FAKE_HASH_replace_me' : cet endpoint met un vrai hash BCrypt
 * pour tous les users, afin de pouvoir se connecter avec les comptes de test.
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean enabled;

    public DevController(UserRepository users, PasswordEncoder encoder,
                         @Value("${app.dev-endpoints-enabled}") boolean enabled) {
        this.users = users;
        this.encoder = encoder;
        this.enabled = enabled;
    }

    public record ResetRequest(String password) {}

    @PostMapping("/reset-passwords")
    public Map<String, Object> resetPasswords(@RequestBody(required = false) ResetRequest r) {
        if (!enabled) throw ApiException.notFound("Not found");
        String pw = (r == null || r.password() == null || r.password().isBlank()) ? "password123" : r.password();
        int n = users.updateAllPasswordHashes(encoder.encode(pw));
        return Map.of("updatedUsers", n, "password", pw);
    }
}
