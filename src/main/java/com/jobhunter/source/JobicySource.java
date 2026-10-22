package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Jobicy public remote-jobs API; one request per target title (at most 3). Jobicy asks for a link back to the listing. UNVERIFIED. */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.jobicy-enabled", havingValue = "true", matchIfMissing = true)
public class JobicySource implements JobSource {
    private final HttpFetcher http;

    public JobicySource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "jobicy";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        Map<String, JobPosting> out = new LinkedHashMap<>();
        for (String title : q.titles().stream().limit(3).toList()) {
            JsonNode root = http.getJson("https://jobicy.com/api/v2/remote-jobs?count=50&tag=" + URLEncoder.encode(title, StandardCharsets.UTF_8));
            for (JsonNode j : root.path("jobs")) {
                String url = j.path("url").asText("");
                String jobTitle = j.path("jobTitle").asText("");
                String ext = j.path("id").asText("");
                if (url.isBlank() || jobTitle.isBlank() || ext.isBlank()) continue;
                String geo = j.path("jobGeo").asText("");
                String desc = j.path("jobDescription").asText(j.path("jobExcerpt").asText(""));
                out.putIfAbsent(ext, new JobPosting(id(), ext, jobTitle, j.path("companyName").asText("Unknown"),
                        geo.isBlank() ? null : geo, true, null, null, desc, url, parse(j.path("pubDate").asText("")), 0));
            }
        }
        return new ArrayList<>(out.values());
    }

    static Instant parse(String s) {
        try {
            return s.length() < 19 ? null : LocalDateTime.parse(s.substring(0, 19).replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
