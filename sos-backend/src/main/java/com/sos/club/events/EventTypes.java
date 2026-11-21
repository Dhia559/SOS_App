package com.sos.club.events;

/**
 * Types d'events et format de leur payload (identique a seed_data.sql).
 *
 *  post.created     {"post_id", "user_id"}
 *  like.created     {"post_id", "user_id"}
 *  comment.created  {"comment_id", "post_id", "user_id"}
 *  friend.requested {"friendship_id"}
 *  friend.accepted  {"friendship_id", "user_id"}      user_id = celui qui accepte
 *  message.sent     {"conversation_id", "sender_id", "message_id"}
 */
public final class EventTypes {
    private EventTypes() {}

    public static final String POST_CREATED = "post.created";
    public static final String LIKE_CREATED = "like.created";
    public static final String COMMENT_CREATED = "comment.created";
    public static final String FRIEND_REQUESTED = "friend.requested";
    public static final String FRIEND_ACCEPTED = "friend.accepted";
    public static final String MESSAGE_SENT = "message.sent";
}
