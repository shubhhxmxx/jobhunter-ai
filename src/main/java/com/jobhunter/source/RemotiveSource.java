package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Remotive public API (remote jobs). UNVERIFIED: written against the documented response shape, not yet run against the live API.
 * Remotive asks consumers to call sparingly and credit them; we make at most 3 requests per run.
 */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.remotive-enabled", havingValue = "true", matchIfMissing = true)
public class RemotiveSource implements JobSource {
    private final HttpFetcher http;

    public RemotiveSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "remotive";
    }

    static java.time.Instant parseDate(String s) {
        try {
            return s.isBlank() ? null : java.time.LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s).toInstant(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        Map<String, JobPosting> out = new LinkedHashMap<>();
        for (String title : q.titles().stream().limit(3).toList()) {
            JsonNode root = http.getJson("https://remotive.com/api/remote-jobs?limit=100&search=" + URLEncoder.encode(title, StandardCharsets.UTF_8));
            for (JsonNode j : root.path("jobs")) {
                String url = j.path("url").asText("");
                String jobTitle = j.path("title").asText("");
                if (url.isBlank() || jobTitle.isBlank()) continue;
                String ext = j.path("id").asText();
                out.putIfAbsent(ext, new JobPosting(id(), ext, jobTitle, j.path("company_name").asText("Unknown"),
                        j.path("candidate_required_location").asText(null), true, null, null,
                        j.path("description").asText(""), url, parseDate(j.path("publication_date").asText("")), 0));
            }
        }
        return new ArrayList<>(out.values());
    }
}
