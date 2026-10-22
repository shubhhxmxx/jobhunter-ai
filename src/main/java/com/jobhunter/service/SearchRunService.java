package com.jobhunter.service;

import com.jobhunter.domain.Preferences;
import com.jobhunter.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchRunService {
    public record SourceRunView(String source, String status, int fetched, String error) {}

    public record RunView(UUID id, String status, int minScore, int jobsFound, int jobsMatched, String error,
                          OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt, List<SourceRunView> sources,
                          Integer eligible, Map<String, Integer> filteredOut) {}

    private final JdbcClient jdbc;
    private final PreferencesService prefs;
    private final ResumeService resumes;

    public SearchRunService(JdbcClient jdbc, PreferencesService prefs, ResumeService resumes) {
        this.jdbc = jdbc;
        this.prefs = prefs;
        this.resumes = resumes;
    }

    /** Validates preconditions and records a QUEUED run. The caller then hands the id to the async worker. */
    public UUID create(UUID candidateId) {
        if (resumes.latest(candidateId).isEmpty()) throw ApiException.conflict("Upload a resume before starting a search");
        Preferences p = prefs.get(candidateId);
        if (p.targetTitles().isEmpty()) throw ApiException.conflict("Set at least one target title in preferences before starting a search");
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO search_run(id,candidate_id,status,min_score,created_at) VALUES(:id,:c,'QUEUED',:m,:t)")
                .param("id", id).param("c", candidateId).param("m", p.minMatchScore()).param("t", OffsetDateTime.now(ZoneOffset.UTC)).update();
        return id;
    }

    public RunView view(UUID candidateId, UUID runId) {
        List<SourceRunView> sources = jdbc.sql("SELECT source,status,fetched,error FROM source_run WHERE search_run_id=:id ORDER BY started_at")
                .param("id", runId).query((rs, i) -> new SourceRunView(rs.getString("source"), rs.getString("status"), rs.getInt("fetched"), rs.getString("error"))).list();
        Integer[] eligible = {null};
        Map<String, Integer> filtered = new LinkedHashMap<>();
        return jdbc.sql("SELECT * FROM search_run WHERE id=:id AND candidate_id=:c").param("id", runId).param("c", candidateId)
                .query((rs, i) -> {
                    String status = rs.getString("status");
                    if (status.startsWith("COMPLETED")) breakdown(candidateId, eligible, filtered);
                    return new RunView(rs.getObject("id", UUID.class), status, rs.getInt("min_score"),
                            rs.getInt("jobs_found"), rs.getInt("jobs_matched"), rs.getString("error"),
                            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                            rs.getObject("finished_at", OffsetDateTime.class), sources, eligible[0], filtered.isEmpty() ? null : filtered);
                })
                .optional().orElseThrow(() -> ApiException.notFound("Search run"));
    }

    /** How many evaluated jobs passed the hard filters, and how many each filter removed. A job can fail several filters. */
    private void breakdown(UUID candidateId, Integer[] eligible, Map<String, Integer> filtered) {
        var resume = resumes.latest(candidateId);
        if (resume.isEmpty()) return;
        String hash = prefs.hash(prefs.get(candidateId));
        String base = "SELECT COUNT(*) FROM job_match WHERE candidate_id=:c AND resume_id=:r AND prefs_hash=:h AND scoring_version=:v AND ";
        java.util.function.Function<String, Integer> count = cond -> jdbc.sql(base + cond)
                .param("c", candidateId).param("r", resume.get().id()).param("h", hash)
                .param("v", com.jobhunter.domain.MatchScorer.SCORING_VERSION).query(Integer.class).single();
        eligible[0] = count.apply("eligible=TRUE");
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("title", "Title does not match");
        patterns.put("location", "outside your target locations");
        patterns.put("remote", "remote is switched off");
        patterns.put("salary", "below your floor");
        patterns.forEach((k, pat) -> {
            int n = count.apply("eligible=FALSE AND rejection_reasons LIKE '%" + pat + "%'");
            if (n > 0) filtered.put(k, n);
        });
    }
}
