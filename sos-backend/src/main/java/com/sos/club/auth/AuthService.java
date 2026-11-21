package com.sos.club.auth;

import com.sos.club.common.ApiException;
import com.sos.club.security.JwtService;
import com.sos.club.user.UserDto;
import com.sos.club.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (r.roleId() != null) {
            if (r.roleId() == 0) throw ApiException.badRequest("roleId 0 (ALL) est reserve aux posts");
            if (!users.roleExists(r.roleId())) throw ApiException.badRequest("Role inconnu");
        }
        if (r.clubId() != null) {
            if (r.clubId() == 0) throw ApiException.badRequest("clubId 0 (ALL) est reserve aux posts");
            if (!users.clubExists(r.clubId())) throw ApiException.badRequest("Club inconnu");
        }
        if (users.emailExists(email)) throw ApiException.conflict("Email deja utilise");

        int id = users.insert(r.firstName().trim(), r.lastName().trim(), email, encoder.encode(r.password()),
                r.birthday(), r.sex(), r.roleId(), r.clubId());
        return response(id, email);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest r) {
        var cred = users.findActiveCredentials(r.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect"));
        if (!encoder.matches(r.password(), cred.passwordHash())) {
            throw ApiException.unauthorized("Email ou mot de passe incorrect");
        }
        return response(cred.id(), cred.email());
    }

    private AuthDtos.AuthResponse response(int id, String email) {
        UserDto user = users.findById(id).orElseThrow();
        return new AuthDtos.AuthResponse(jwt.generate(id, email), "Bearer", jwt.ttlSeconds(), user);
    }
}
