package com.sos.club.feed;

import com.sos.club.feed.FeedDtos.*;
import com.sos.club.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {
    private final PostService posts;

    public PostController(PostService posts) { this.posts = posts; }

    /** ?before=<nextBefore de la page precedente> (ISO-8601) */
    @GetMapping("/feed")
    public FeedPage feed(@AuthenticationPrincipal AuthUser me,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before,
                         @RequestParam(defaultValue = "20") int limit) {
        return posts.feed(me.id(), before, limit);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto create(@AuthenticationPrincipal AuthUser me, @Validated @RequestBody CreatePostRequest r) {
        return posts.create(me.id(), r);
    }

    @GetMapping("/posts/{id}")
    public PostDto get(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return posts.get(me.id(), id);
    }

    @PutMapping("/posts/{id}")
    public PostDto update(@AuthenticationPrincipal AuthUser me, @PathVariable int id, @Validated @RequestBody UpdatePostRequest r) {
        return posts.update(me.id(), id, r);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        posts.delete(me.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- likes

    @PostMapping("/posts/{id}/like")
    public LikeState like(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return posts.like(me.id(), id);
    }

    @DeleteMapping("/posts/{id}/like")
    public LikeState unlike(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return posts.unlike(me.id(), id);
    }

    @GetMapping("/posts/{id}/likes")
    public List<LikerDto> likers(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return posts.likers(me.id(), id);
    }

    // ---- comments

    @GetMapping("/posts/{id}/comments")
    public List<CommentDto> comments(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        return posts.comments(me.id(), id);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@AuthenticationPrincipal AuthUser me, @PathVariable int id,
                                 @Validated @RequestBody CreateCommentRequest r) {
        return posts.addComment(me.id(), id, r);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser me, @PathVariable int id) {
        posts.deleteComment(me.id(), id);
        return ResponseEntity.noContent().build();
    }
}
