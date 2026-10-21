package com.jobhunter.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpFetcher {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper;

    public HttpFetcher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "JobHunterAI/0.1 (personal job search)").header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            throw new IllegalStateException("Rate limited by source (Retry-After: " + res.headers().firstValue("Retry-After").orElse("unknown") + ")");
        }
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("Source returned HTTP " + res.statusCode());
        return mapper.readTree(res.body());
    }
}
