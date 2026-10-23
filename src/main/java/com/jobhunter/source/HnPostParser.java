package com.jobhunter.source;

import com.jobhunter.domain.JobNormalizer;
import com.jobhunter.domain.JobPosting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses top-level comments of the monthly "Ask HN: Who is hiring?" thread. By convention the first line is "Company | Role | Location | ...". */
public final class HnPostParser {
    private static final Pattern ROLE = Pattern.compile(
            "(?i)engineer|developer|sde|sre|architect|devops|scientist|manager|lead|designer|intern|programmer");
    private static final Pattern NOT_REMOTE = Pattern.compile("(?i)no remote|not remote|onsite only|on-site only");
    private static final Pattern REMOTE = Pattern.compile("(?i)\\bremote\\b");

    private HnPostParser() {}

    private static final Pattern URL = Pattern.compile("https?://[^\\s|)]+");

    /** Returns null when the comment does not follow the header convention. */
    public static JobPosting parse(String commentHtml, String itemId) {
        if (commentHtml == null || itemId == null) return null;
        String headerHtml = commentHtml.split("(?i)<p>", 2)[0];
        String header = JobNormalizer.stripHtml(headerHtml).split("\\n")[0].trim();
        String[] raw = header.split("\\|");
        if (raw.length < 2) return null;

        // The header often runs straight into the intro paragraph, so keep only short fields and pull out the job link.
        List<String> fields = new ArrayList<>();
        String applyUrl = null;
        boolean visaFlag = false;
        for (String f : raw) {
            f = f.trim();
            var m = URL.matcher(f);
            if (m.find()) { if (applyUrl == null) applyUrl = m.group().replaceAll("[.,;]+$", ""); continue; }
            if (f.equalsIgnoreCase("visa") || f.equalsIgnoreCase("visa sponsorship")) { visaFlag = true; continue; }
            if (!f.isEmpty() && f.length() <= 80) fields.add(f);
        }
        if (fields.size() < 2) return null;

        String company = fields.get(0);
        String title = fields.stream().skip(1).filter(f -> ROLE.matcher(f).find()).findFirst().orElse(fields.get(1));
        final String chosen = title;
        String location = String.join(", ", fields.stream().skip(1).filter(f -> !f.equals(chosen)).limit(3).toList());
        String head = String.join(" | ", fields);
        boolean remote = REMOTE.matcher(head).find() && !NOT_REMOTE.matcher(head).find();
        String description = JobNormalizer.stripHtml(commentHtml.replaceAll("(?i)<p>", "\n"));
        if (visaFlag) description = "Visa sponsorship available (stated in the post header).\n" + description;
        String url = applyUrl != null ? applyUrl : "https://news.ycombinator.com/item?id=" + itemId;
        return new JobPosting("hackernews", itemId, title, company, location.isBlank() ? null : location, remote,
                null, null, description, url, null, 0);
    }
}
