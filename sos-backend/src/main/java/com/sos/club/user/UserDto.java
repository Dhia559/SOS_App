package com.sos.club.user;

import java.time.LocalDate;

public record UserDto(
        int id,
        String firstName,
        String lastName,
        String email,
        LocalDate birthday,
        String sex,
        Integer roleId,
        String roleName,
        Integer clubId,
        String clubName,
        boolean active) {
}
