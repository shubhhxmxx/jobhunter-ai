package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobPosting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Adzuna job-search API (aggregates many Indian job boards). Needs a free App ID and key from developer.adzuna.com,
 * set as JOBHUNTER_ADZUNA_APP_ID and JOBHUNTER_ADZUNA_APP_KEY; without them this source is simply not loaded.
 * Descriptions are short snippets, so skill matching is weaker than for full postings. At most 12 requests per run. UNVERIFIED.
 */
@Component
@ConditionalOnExpression("'${jobhunter.sources.adzuna-app-id:}' != '' and '${jobhunter.sources.adzuna-app-key:}' != ''")
public class AdzunaSource implements JobSource {
    private final HttpFetcher http;
    private final String appId;
    private final String appKey;
    private final String country;

    public AdzunaSource(HttpFetcher http, @Value("${jobhunter.sources.adzuna-app-id}") String appId,
                        @Value("${jobhunter.sources.adzuna-app-key}") String appKey,
                        @Value("${jobhunter.sources.adzuna-country:in}") String country) {
        this.http = http;
        this.appId = appId;
        this.appKey = appKey;
        this.country = country;
    }

    @Override
    public String id() {
        return "adzuna";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        List<String> places = q.locations().isEmpty() ? List.of("") : q.locations().stream().limit(2).toList();
        Map<String, JobPosting> out = new LinkedHashMap<>();
        int requests = 0;
        for (String title : q.titles().stream().limit(3).toList()) {
            for (String place : places) {
                for (int page = 1; page <= 2 && requests < 12; page++, requests++) {
                    String url = "https://api.adzuna.com/v1/api/jobs/" + country + "/search/" + page + "?app_id=" + enc(appId)
                            + "&app_key=" + enc(appKey) + "&results_per_page=50&what=" + enc(title)
                            + (place.isBlank() ? "" : "&where=" + enc(place)) + "&content-type=application/json";
                    JsonNode results = http.getJson(url).path("results");
                    if (!results.isArray() || results.isEmpty()) break;
                    for (JsonNode j : results) {
                        String ext = j.path("id").asText("");
                        String redirect = j.path("redirect_url").asText("");
                        String jobTitle = j.path("title").asText("");
                        if (ext.isBlank() || redirect.isBlank() || jobTitle.isBlank()) continue;
                        boolean predicted = "1".equals(j.path("salary_is_predicted").asText("0"));
                        Integer smin = !predicted && j.hasNonNull("salary_min") && country.equals("in") ? (int) Math.floor(j.path("salary_min").asDouble() / 100000.0) : null;
                        Integer smax = !predicted && j.hasNonNull("salary_max") && country.equals("in") ? (int) Math.ceil(j.path("salary_max").asDouble() / 100000.0) : null;
                        Instant posted = null;
                        try { posted = Instant.parse(j.path("created").asText("")); } catch (Exception ignored) { }
                        out.putIfAbsent(ext, new JobPosting(id(), ext, jobTitle, j.path("company").path("display_name").asText("Unknown"),
                                j.path("location").path("display_name").asText(null), false, smin, smax,
                                j.path("description").asText(""), redirect, posted, 0));
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }
}
