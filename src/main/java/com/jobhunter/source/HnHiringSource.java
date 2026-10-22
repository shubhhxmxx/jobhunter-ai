package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobNormalizer;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hacker News "Ask HN: Who is hiring?" via the free HN Algolia API (about 3 requests per run).
 * UNVERIFIED against the live API. Only posts whose title matches your target titles are kept.
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
        JsonNode stories = http.getJson("https://hn.algolia.com/api/v1/search_by_date?tags=story,author_whoishiring&hitsPerPage=10");
        String storyId = null;
        for (JsonNode h : stories.path("hits")) {
            if (h.path("title").asText("").toLowerCase().contains("who is hiring")) { storyId = h.path("objectID").asText(); break; }
        }
        if (storyId == null) throw new IllegalStateException("Could not find the current Who is hiring thread");

        List<JobPosting> out = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            JsonNode r = http.getJson("https://hn.algolia.com/api/v1/search?tags=comment,story_" + storyId + "&hitsPerPage=1000&page=" + page);
            for (JsonNode c : r.path("hits")) {
                if (!storyId.equals(c.path("parent_id").asText())) continue; // top-level posts only
                JobPosting p = HnPostParser.parse(c.path("comment_text").asText(""), c.path("objectID").asText());
                if (p != null && JobNormalizer.titleMatches(p.title(), q.titles())) out.add(p);
            }
            if (page >= r.path("nbPages").asInt(1) - 1) break;
        }
        return out;
    }
}
