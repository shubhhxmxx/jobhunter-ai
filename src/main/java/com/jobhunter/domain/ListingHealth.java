package com.jobhunter.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Warnings about listings that may already be filled or abandoned. Heuristics only; they cannot prove a listing is fake. */
public final class ListingHealth {
    public static final int STALE_DAYS = 45;

    private ListingHealth() {}

    public static List<String> warnings(Instant postedAt, int repostCount, Instant now) {
        List<String> w = new ArrayList<>();
        if (postedAt != null) {
            long days = Duration.between(postedAt, now).toDays();
            if (days > STALE_DAYS) w.add("Posted " + days + " days ago. Older listings are often filled or abandoned");
        }
        if (repostCount >= 2) {
            w.add("Also listed in the previous " + repostCount + " monthly Hacker News threads. It may be a repost or a ghost listing");
        }
        return w;
    }
}
