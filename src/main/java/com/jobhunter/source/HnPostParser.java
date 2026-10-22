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

    /** Returns null when the comment does not follow the header convention. */
    public static JobPosting parse(String commentHtml, String itemId) {
        if (commentHtml == null || itemId == null) return null;
        String headerHtml = commentHtml.split("(?i)<p>", 2)[0];
        String header = JobNormalizer.stripHtml(headerHtml).split("\\n")[0].trim();
        String[] raw = header.split("\\|");
        if (raw.length < 2) return null;
        List<String> fields = new ArrayList<>();
        for (String f : raw) if (!f.isBlank()) fields.add(f.trim());
        if (fields.size() < 2) return null;

        String company = fields.get(0);
        if (company.length() > 80) return null;
        String title = fields.stream().skip(1).filter(f -> ROLE.matcher(f).find()).findFirst().orElse(fields.get(1));
        if (title.length() > 200) title = title.substring(0, 200);
        final String chosen = title;
        String location = String.join(", ", fields.stream().skip(1).filter(f -> !f.equals(chosen)).limit(3).toList());
        boolean remote = REMOTE.matcher(header).find() && !NOT_REMOTE.matcher(header).find();
        String description = JobNormalizer.stripHtml(commentHtml.replaceAll("(?i)<p>", "\n"));
        return new JobPosting("hackernews", itemId, title, company, location.isBlank() ? null : location, remote,
                null, null, description, "https://news.ycombinator.com/item?id=" + itemId);
    }
}
