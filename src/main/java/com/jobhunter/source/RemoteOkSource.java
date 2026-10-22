package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** RemoteOK public JSON feed (remote jobs, one request per run). RemoteOK requires linking back to the listing and crediting it as the source. UNVERIFIED. */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.remoteok-enabled", havingValue = "true", matchIfMissing = true)
public class RemoteOkSource implements JobSource {
    private final HttpFetcher http;

    public RemoteOkSource(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public String id() {
        return "remoteok";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        JsonNode root = http.getJson("https://remoteok.com/api");
        List<JobPosting> out = new ArrayList<>();
        for (JsonNode j : root) {
            String position = j.path("position").asText("");
            String url = j.path("url").asText("");
            String ext = j.path("id").asText("");
            if (position.isBlank() || url.isBlank() || ext.isBlank()) continue; // the first element is a legal notice
            long epoch = j.path("epoch").asLong(0);
            String loc = j.path("location").asText("");
            out.add(new JobPosting(id(), ext, position, j.path("company").asText("Unknown"), loc.isBlank() ? null : loc, true,
                    null, null, j.path("description").asText(""), url, epoch > 0 ? Instant.ofEpochSecond(epoch) : null, 0));
        }
        return out;
    }
}
