package com.jobhunter.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunter.domain.JobPosting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/** FIXTURE data for demos and tests. Source id is "fixture" and every URL is under example.invalid, so it can never pass as a real listing. */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.fixtures-enabled", havingValue = "true", matchIfMissing = true)
public class FixtureJobSource implements JobSource {
    private final ObjectMapper mapper;

    public FixtureJobSource(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return "fixture";
    }

    @Override
    public List<JobPosting> fetch(Query q) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/jobs.json")) {
            return mapper.readValue(in, new TypeReference<List<JobPosting>>() {});
        }
    }
}
