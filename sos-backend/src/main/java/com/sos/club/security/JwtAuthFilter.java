package com.sos.club.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Lit "Authorization: Bearer <jwt>" et place l'AuthUser dans le SecurityContext. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parse(header.substring(7).trim()).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(toAuthentication(user)));
        }
        chain.doFilter(req, res);
    }

    public static UsernamePasswordAuthenticationToken toAuthentication(AuthUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
