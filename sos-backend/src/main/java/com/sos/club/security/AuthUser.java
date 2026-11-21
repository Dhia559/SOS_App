package com.sos.club.security;

import java.security.Principal;

/**
 * Utilisateur authentifie (extrait du JWT). Injecte dans les controllers via
 * {@code @AuthenticationPrincipal AuthUser me}. getName() = id, utilise par STOMP
 * pour les destinations /user/{id}/queue/...
 */
public record AuthUser(int id, String email) implements Principal {
    @Override
    public String getName() { return String.valueOf(id); }
}
