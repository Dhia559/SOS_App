package com.sos.club.feed;

import com.sos.club.common.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class FeedDtos {
    private FeedDtos() {}

    public record PostDto(
            int id,
            String content,
            UserSummary author,
            int visibleRoleId,
            String visibleRoleName,
            int visibleClubId,
            String visibleClubName,
            OffsetDateTime createdAt,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            boolean hasImage,
            PostEvent event) {}

    /** Evenement attache a un post (icone calendrier). null si le post n'en a pas. */
    public record PostEvent(String title, OffsetDateTime startsAt, String place) {}

    /** Ligne du panneau "A venir". */
    public record UpcomingEvent(int postId, String title, OffsetDateTime startsAt, String place, UserSummary author) {}

    public record CommentDto(int id, int postId, UserSummary author, String content, OffsetDateTime createdAt) {}

    public record LikeState(int postId, boolean liked, long likeCount) {}

    public record LikerDto(UserSummary user, OffsetDateTime likedAt) {}

    /**
     * visibleRoleId / visibleClubId : 0 ou absent = tout le monde.
     * image : data URL optionnelle ("data:image/jpeg;base64,..."), 5 Mo max.
     * eventTitle + eventStartsAt (+ eventPlace) : evenement optionnel.
     */
    public record CreatePostRequest(
            @NotBlank @Size(max = 5000) String content,
            Integer visibleRoleId,
            Integer visibleClubId,
            String image,
            @Size(max = 150) String eventTitle,
            OffsetDateTime eventStartsAt,
            @Size(max = 150) String eventPlace) {}

    public record UpdatePostRequest(@NotBlank @Size(max = 5000) String content) {}

    public record CreateCommentRequest(@NotBlank @Size(max = 2000) String content) {}
}
