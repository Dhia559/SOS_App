package com.sos.club.common;

/** Paires triees (user1_id < user2_id) pour friendships et conversations. */
public final class Pairs {
    private Pairs() {}

    public static int low(int a, int b)  { return Math.min(a, b); }
    public static int high(int a, int b) { return Math.max(a, b); }

    public static String excerpt(String s, int max) {
        if (s == null) return "";
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
