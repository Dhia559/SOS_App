package com.sos.club.common;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Version courte d'un user, embarquee dans les posts, commentaires, amis, conversations.
 * Le SQL doit exposer les colonnes {prefix}id, {prefix}first_name, {prefix}last_name,
 * {prefix}role_name, {prefix}club_name.
 */
public record UserSummary(int id, String firstName, String lastName, String roleName, String clubName) {

    public static UserSummary from(ResultSet rs, String prefix) throws SQLException {
        return new UserSummary(
                rs.getInt(prefix + "id"),
                rs.getString(prefix + "first_name"),
                rs.getString(prefix + "last_name"),
                rs.getString(prefix + "role_name"),
                rs.getString(prefix + "club_name"));
    }

    public String fullName() { return firstName + " " + lastName; }
}
