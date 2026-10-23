package com.jobhunter.service;

import com.jobhunter.domain.JobPosting;
import com.jobhunter.domain.MatchScorer;
import com.jobhunter.domain.Preferences;
import com.jobhunter.source.JobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs a search in the background: fetch from every enabled source, persist and dedupe, then filter and score. */
@Component
public class SearchRunWorker {
    private static final Logger log = LoggerFactory.getLogger(SearchRunWorker.class);

    private final JdbcClient jdbc;
    private final List<JobSource> sources;
    private final JobRepository jobs;
    private final MatchService matcher;
    private final PreferencesService prefsService;
    private final ResumeService resumes;

    public SearchRunWorker(JdbcClient jdbc, List<JobSource> sources, JobRepository jobs, MatchService matcher,
                           PreferencesService prefsService, ResumeService resumes) {
        this.jdbc = jdbc;
        this.sources = sources;
        this.jobs = jobs;
        this.matcher = matcher;
        this.prefsService = prefsService;
        this.resumes = resumes;
    }

    @Async("searchExecutor")
    public void execute(UUID runId) {
        try {
            run(runId);
        } catch (Exception e) {
            log.error("Search run {} failed: {}", runId, e.getClass().getSimpleName());
            finish(runId, "FAILED", 0, 0, "Search failed unexpectedly");
        }
    }

    private void run(UUID runId) {
        UUID candidateId = jdbc.sql("SELECT candidate_id FROM search_run WHERE id=:id").param("id", runId).query(UUID.class).single();
        jdbc.sql("UPDATE search_run SET status='RUNNING', started_at=:t WHERE id=:id").param("t", now()).param("id", runId).update();

        Preferences prefs = prefsService.get(candidateId);
        ResumeService.Current resume = resumes.latest(candidateId).orElseThrow();
        String hash = prefsService.hash(prefs);
        JobSource.Query query = new JobSource.Query(prefs.targetTitles(), prefs.locations(), prefs.remoteOk());

        if (sources.isEmpty()) { finish(runId, "FAILED", 0, 0, "No job sources are enabled"); return; }
        Set<UUID> found = new HashSet<>();
        int ok = 0, failed = 0;
        for (JobSource source : sources) {
            UUID srId = UUID.randomUUID();
            jdbc.sql("INSERT INTO source_run(id,search_run_id,source,status,started_at) VALUES(:id,:r,:s,'RUNNING',:t)")
                    .param("id", srId).param("r", runId).param("s", source.id()).param("t", now()).update();
            try {
                int n = 0;
                for (JobPosting p : source.fetch(query)) { found.add(jobs.upsert(p)); n++; }
                jdbc.sql("UPDATE source_run SET status='COMPLETED', fetched=:n, finished_at=:t WHERE id=:id")
                        .param("n", n).param("t", now()).param("id", srId).update();
                ok++;
            } catch (Exception e) {
                log.warn("Source {} failed in run {}: {}", source.id(), runId, e.getClass().getSimpleName());
                jdbc.sql("UPDATE source_run SET status='FAILED', error=:e, finished_at=:t WHERE id=:id")
                        .param("e", safe(e)).param("t", now()).param("id", srId).update();
                failed++;
            }
        }
        if (ok == 0) { finish(runId, "FAILED", 0, 0, "Every job source failed. See sources for details"); return; }

        for (JobRepository.Row row : jobs.openJobs(6000)) matcher.evaluate(candidateId, resume, prefs, hash, row);
        int matched = jdbc.sql("SELECT COUNT(*) FROM job_match WHERE candidate_id=:c AND resume_id=:r AND prefs_hash=:h AND scoring_version=:v AND eligible=TRUE AND score>=:m")
                .param("c", candidateId).param("r", resume.id()).param("h", hash).param("v", MatchScorer.SCORING_VERSION)
                .param("m", prefs.minMatchScore()).query(Integer.class).single();
        finish(runId, failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS", found.size(), matched, null);
    }

    private void finish(UUID runId, String status, int found, int matched, String error) {
        jdbc.sql("UPDATE search_run SET status=:s, jobs_found=:f, jobs_matched=:m, error=:e, finished_at=:t WHERE id=:id")
                .param("s", status).param("f", found).param("m", matched).param("e", error).param("t", now()).param("id", runId).update();
    }

    private static String safe(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        m = com.jobhunter.security.Redactor.redact(m);
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
