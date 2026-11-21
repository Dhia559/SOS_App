package com.sos.club.user;

import com.sos.club.common.ApiException;
import com.sos.club.feed.FeedPage;
import com.sos.club.feed.PostService;
import com.sos.club.security.AuthUser;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    private final PostService posts;

    public UserController(UserRepository users, PostService posts) {
        this.users = users;
        this.posts = posts;
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 50) String firstName,
            @Size(min = 1, max = 50) String lastName,
            LocalDate birthday,
            @Pattern(regexp = "[MF]") String sex) {}

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return get(me.id());
    }

    @PutMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthUser me, @Validated @RequestBody UpdateProfileRequest r) {
        users.updateProfile(me.id(), r.firstName(), r.lastName(), r.birthday(), r.sex());
        return get(me.id());
    }

    @GetMapping("/{id}")
    public UserDto byId(@PathVariable int id) {
        return get(id);
    }

    @GetMapping
    public List<UserDto> search(@RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "20") int limit) {
        return users.search(search, Math.min(Math.max(limit, 1), 100));
    }

    /** Posts d'un user que le viewer a le droit de voir (page profil). */
    @GetMapping("/{id}/posts")
    public FeedPage postsOf(@AuthenticationPrincipal AuthUser me, @PathVariable int id,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before,
                            @RequestParam(defaultValue = "20") int limit) {
        return posts.postsOfAuthor(me.id(), id, before, limit);
    }

    private UserDto get(int id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }
}
