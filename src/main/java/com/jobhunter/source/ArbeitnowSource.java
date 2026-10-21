package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Arbeitnow public job-board API (mostly Europe). UNVERIFIED against the live API; one request per run. */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.arbeitnow-enabled", havingValue = "true", matchIfMissing = true)
public class ArbeitnowSource implements JobSource {
    private final HttpFetcher http;

    public ArbeitnowSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "arbeitnow";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        JsonNode root = http.getJson("https://www.arbeitnow.com/api/job-board-api");
        List<JobPosting> out = new ArrayList<>();
        for (JsonNode j : root.path("data")) {
            String url = j.path("url").asText("");
            String title = j.path("title").asText("");
            String slug = j.path("slug").asText("");
            if (url.isBlank() || title.isBlank() || slug.isBlank()) continue;
            out.add(new JobPosting(id(), slug, title, j.path("company_name").asText("Unknown"),
                    j.path("location").asText(null), j.path("remote").asBoolean(false), null, null,
                    j.path("description").asText(""), url));
        }
        return out;
    }
}
