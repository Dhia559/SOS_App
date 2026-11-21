package com.sos.club.user;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Listes pour les menus deroulants. L'id 0 = "ALL" (cible "tout le monde" d'un post). */
@RestController
@RequestMapping("/api")
public class LookupController {
    public record Lookup(int id, String name, boolean all) {}

    private final NamedParameterJdbcTemplate jdbc;

    public LookupController(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/roles")
    public List<Lookup> roles() {
        return jdbc.query("SELECT id, name FROM roles ORDER BY id", Map.of(),
                (rs, i) -> new Lookup(rs.getInt("id"), rs.getString("name"), rs.getInt("id") == 0));
    }

    @GetMapping("/clubs")
    public List<Lookup> clubs() {
        return jdbc.query("SELECT id, name FROM clubs ORDER BY id", Map.of(),
                (rs, i) -> new Lookup(rs.getInt("id"), rs.getString("name"), rs.getInt("id") == 0));
    }
}
