package com.sos.club.auth;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Validated @RequestBody AuthDtos.RegisterRequest r) {
        return auth.register(r);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Validated @RequestBody AuthDtos.LoginRequest r) {
        return auth.login(r);
    }
}
