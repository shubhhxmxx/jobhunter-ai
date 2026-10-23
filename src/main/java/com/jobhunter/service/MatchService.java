package com.jobhunter.service;

import com.jobhunter.domain.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class MatchService {
    private final JdbcClient jdbc;
    private final Json json;

    public MatchService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Filters first (deterministic), then scores (deterministic). A result already stored for the same
     * (candidate, job, resume, preferences hash, scoring version) is reused, so nothing is recomputed.
     */
    public void evaluate(UUID candidateId, ResumeService.Current resume, Preferences prefs, String prefsHash, JobRepository.Row job) {
        boolean cached = jdbc.sql("SELECT COUNT(*) FROM job_match WHERE candidate_id=:c AND job_id=:j AND resume_id=:r AND prefs_hash=:h AND scoring_version=:v")
                .param("c", candidateId).param("j", job.id()).param("r", resume.id()).param("h", prefsHash)
                .param("v", MatchScorer.SCORING_VERSION).query(Integer.class).single() > 0;
        if (cached) return;

        Integer years = prefs.yearsExperience() != null ? prefs.yearsExperience() : resume.parsed().yearsExperience();
        Candidate candidate = new Candidate(years, new java.util.HashSet<>(resume.parsed().skills()));
        List<String> reasons = EligibilityFilter.reasons(job.posting(), prefs, candidate);
        MatchResult m = reasons.isEmpty() ? MatchScorer.score(job.posting(), resume.parsed(), prefs)
                : new MatchResult(0, List.of(), List.of(), List.of(), List.of(), "rejected", "", false, "UNKNOWN");
        try {
            jdbc.sql("INSERT INTO job_match(id,candidate_id,job_id,resume_id,resume_version,prefs_hash,scoring_version,eligible,score,"
                            + "rejection_reasons,matched_skills,missing_requirements,evidence,uncertainty,created_at,work_mode,work_label,relocation,visa) "
                            + "VALUES(:id,:c,:j,:r,:rv,:h,:v,:e,:s,:rr,:ms,:mr,:ev,:u,:t,:wm,:wl,:rl,:vs)")
                    .param("id", UUID.randomUUID()).param("c", candidateId).param("j", job.id()).param("r", resume.id())
                    .param("rv", resume.version()).param("h", prefsHash).param("v", MatchScorer.SCORING_VERSION)
                    .param("e", reasons.isEmpty()).param("s", m.score()).param("rr", json.write(reasons))
                    .param("ms", json.write(m.matchedSkills())).param("mr", json.write(m.missingRequirements()))
                    .param("ev", json.write(m.evidence())).param("u", json.write(m.uncertainty()))
                    .param("t", OffsetDateTime.now(ZoneOffset.UTC))
                    .param("wm", m.workMode()).param("wl", m.workLabel()).param("rl", m.relocation()).param("vs", m.visa()).update();
        } catch (DuplicateKeyException ignored) {
            // another run scored the same job concurrently; its row is equivalent
        }
    }
}
