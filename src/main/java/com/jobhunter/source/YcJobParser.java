package com.jobhunter.source;

import com.jobhunter.domain.JobPosting;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Hacker News job posts, which Y Combinator publishes as "Company (YC W23) Is Hiring a Senior Backend Engineer in Boston". */
public final class YcJobParser {
    private static final Pattern HIRING = Pattern.compile("(?i)^(.+?)\\s+(?:is|are)\\s+hiring\\s*(?:an?\\s+)?(.*)$");
    private static final Pattern YC_TAG = Pattern.compile("(?i)\\s*\\((?:YC|Y Combinator)[^)]*\\)");
    private static final Pattern REMOTE = Pattern.compile("(?i)\\bremote\\b");
    private static final Pattern IN_PLACE = Pattern.compile("(?i)\\s+in\\s+([^()]{2,60}?)\\s*(?:\\(.*\\))?$");

    private YcJobParser() {}

    /** Returns null when the title is not a "... is hiring ..." post. The post has no description, only its title. */
    public static JobPosting parse(String title, String url, String id, Instant postedAt) {
        if (title == null || id == null) return null;
        Matcher m = HIRING.matcher(title.trim());
        if (!m.find()) return null;
        String company = YC_TAG.matcher(m.group(1)).replaceAll("").trim();
        String role = m.group(2).trim();
        if (company.isEmpty() || role.isEmpty()) return null;
        boolean remote = REMOTE.matcher(role).find();
        String location = null;
        Matcher in = IN_PLACE.matcher(role);
        if (in.find()) {
            location = in.group(1).trim();
            role = role.substring(0, in.start()).trim();
        } else if (remote) {
            location = "Remote";
        }
        role = role.replaceAll("(?i)\\s*\\(?\\bremote\\b\\)?\\s*$", "").trim();
        if (role.isEmpty()) return null;
        String link = url != null && url.matches("(?i)^https?://\\S+$") ? url : "https://news.ycombinator.com/item?id=" + id;
        return new JobPosting("ycjobs", id, role, company, location, remote, null, null, title.trim(), link, postedAt, 0);
    }
}
