package com.sos.club.feed;

import java.time.OffsetDateTime;
import java.util.List;

/** Page de feed. Pour la page suivante : ?before=nextBefore (null = plus rien). */
public record FeedPage(List<FeedDtos.PostDto> posts, OffsetDateTime nextBefore) {
}
