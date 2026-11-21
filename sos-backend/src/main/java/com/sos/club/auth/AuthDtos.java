package com.sos.club.auth;

import com.sos.club.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String firstName,
            @NotBlank @Size(max = 50) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            LocalDate birthday,
            @Pattern(regexp = "[MF]") String sex,
            Integer roleId,
            Integer clubId) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String tokenType, long expiresIn, UserDto user) {}
}
