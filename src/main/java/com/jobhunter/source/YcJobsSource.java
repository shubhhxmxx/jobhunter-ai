package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobNormalizer;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Job posts that Y Combinator publishes on Hacker News (news.ycombinator.com/jobs), read through the free HN Algolia API in one request.
 * YC's own "Work at a Startup" board needs a login and has no public API, so it is not used. Posts carry only a title and a link,
 * so skills cannot be matched and a post that does not say where the job is will not pass the location rules. UNVERIFIED against the live API.
 */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.ycjobs-enabled", havingValue = "true", matchIfMissing = true)
public class YcJobsSource implements JobSource {
    private final HttpFetcher http;

    public YcJobsSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "ycjobs";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        JsonNode hits = http.getJson("https://hn.algolia.com/api/v1/search_by_date?tags=job&hitsPerPage=200").path("hits");
        List<JobPosting> out = new ArrayList<>();
        for (JsonNode h : hits) {
            long ts = h.path("created_at_i").asLong(0);
            JobPosting p = YcJobParser.parse(h.path("title").asText(""), h.path("url").asText(""), h.path("objectID").asText(""),
                    ts > 0 ? Instant.ofEpochSecond(ts) : null);
            if (p != null && JobNormalizer.titleMatches(p.title(), q.titles())) out.add(p);
        }
        return out;
    }
}
