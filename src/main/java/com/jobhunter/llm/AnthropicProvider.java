package com.jobhunter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API client. NOTE: implemented but not yet exercised by tests or used by the matching flow. */
@Component
public class AnthropicProvider implements LlmProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;

    public AnthropicProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "anthropic";
    }

    @Override
    public String complete(String apiKey, String model, String system, String user) throws Exception {
        String body = mapper.writeValueAsString(Map.of("model", model, "max_tokens", 1024, "system", system,
                "messages", List.of(Map.of("role", "user", "content", user))));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60)).header("content-type", "application/json")
                .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("Anthropic API returned HTTP " + res.statusCode());
        JsonNode text = mapper.readTree(res.body()).path("content").path(0).path("text");
        return text.asText();
    }
}
