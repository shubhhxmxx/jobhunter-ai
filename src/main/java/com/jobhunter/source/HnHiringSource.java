package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobNormalizer;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Hacker News "Ask HN: Who is hiring?" via the free HN Algolia API (about 7 requests per run: the thread list plus
 * two pages for each of the current and two previous monthly threads). Only posts whose role matches your target
 * titles are kept. A post whose company and role also appeared in earlier threads gets a repostCount, which the UI
 * turns into a repost/ghost-listing warning. UNVERIFIED against the live API beyond a first manual check.
 */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.hackernews-enabled", havingValue = "true", matchIfMissing = true)
public class HnHiringSource implements JobSource {
    private final HttpFetcher http;

    public HnHiringSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "hackernews";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        JsonNode stories = http.getJson("https://hn.algolia.com/api/v1/search_by_date?tags=story,author_whoishiring&hitsPerPage=12");
        List<String> threads = new ArrayList<>();
        for (JsonNode h : stories.path("hits")) {
            if (threads.size() < 3 && h.path("title").asText("").toLowerCase().contains("who is hiring")) threads.add(h.path("objectID").asText());
        }
        if (threads.isEmpty()) throw new IllegalStateException("Could not find the current Who is hiring thread");

        Map<String, Integer> seenBefore = new HashMap<>();
        for (String older : threads.subList(1, threads.size())) {
            Set<String> keys = new HashSet<>();
            for (JobPosting p : fetchThread(older)) keys.add(roleKey(p));
            for (String k : keys) seenBefore.merge(k, 1, Integer::sum);
        }

        List<JobPosting> out = new ArrayList<>();
        for (JobPosting p : fetchThread(threads.get(0))) {
            if (JobNormalizer.titleMatches(p.title(), q.titles())) out.add(p.withListingInfo(p.postedAt(), seenBefore.getOrDefault(roleKey(p), 0)));
        }
        return out;
    }

    private static String roleKey(JobPosting p) {
        return JobNormalizer.key(p.company()) + "|" + JobNormalizer.key(p.title());
    }

    /** Top-level posts of one monthly thread. */
    private List<JobPosting> fetchThread(String storyId) throws Exception {
        List<JobPosting> out = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            JsonNode r = http.getJson("https://hn.algolia.com/api/v1/search?tags=comment,story_" + storyId + "&hitsPerPage=1000&page=" + page);
            for (JsonNode c : r.path("hits")) {
                if (!storyId.equals(c.path("parent_id").asText())) continue; // top-level posts only
                JobPosting p = HnPostParser.parse(c.path("comment_text").asText(""), c.path("objectID").asText());
                if (p == null) continue;
                long ts = c.path("created_at_i").asLong(0);
                out.add(p.withListingInfo(ts > 0 ? Instant.ofEpochSecond(ts) : null, 0));
            }
            if (page >= r.path("nbPages").asInt(1) - 1) break;
        }
        return out;
    }
}
