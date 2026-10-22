package com.jobhunter.service;

import com.jobhunter.domain.ListingHealth;
import com.jobhunter.domain.MatchScorer;
import com.jobhunter.domain.Preferences;
import com.jobhunter.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobQueryService {
    public record MatchView(boolean eligible, int score, List<String> matchedSkills, List<String> missingRequirements,
                            List<String> evidence, List<String> uncertainty, List<String> rejectionReasons, int resumeVersion) {}

    public record JobView(UUID id, String source, boolean fixture, String title, String company, String location, boolean remote,
                          Integer salaryMin, Integer salaryMax, String url, OffsetDateTime retrievedAt, OffsetDateTime postedAt, int repostCount,
                          List<String> warnings, String description, MatchView match) {}

    private static final String COLS = "SELECT j.id,j.source,j.title,j.location,j.remote,j.salary_min,j.salary_max,j.source_url,j.retrieved_at,j.posted_at,j.repost_count,j.description,"
            + "c.name AS company_name,m.eligible AS m_eligible,m.score AS m_score,m.matched_skills,m.missing_requirements,m.evidence,"
            + "m.uncertainty,m.rejection_reasons,m.resume_version ";

    private final JdbcClient jdbc;
    private final Json json;
    private final ResumeService resumes;
    private final PreferencesService prefsService;

    public JobQueryService(JdbcClient jdbc, Json json, ResumeService resumes, PreferencesService prefsService) {
        this.jdbc = jdbc;
        this.json = json;
        this.resumes = resumes;
        this.prefsService = prefsService;
    }

    /** Ranked, eligible matches for the candidate's latest resume and current preferences. */
    public List<JobView> ranked(UUID candidateId, Integer minScore, int limit, int offset) {
        Optional<ResumeService.Current> resume = resumes.latest(candidateId);
        if (resume.isEmpty()) return List.of();
        Preferences prefs = prefsService.get(candidateId);
        int min = minScore != null ? minScore : prefs.minMatchScore();
        return jdbc.sql(COLS + "FROM job_match m JOIN job j ON j.id=m.job_id JOIN company c ON c.id=j.company_id "
                        + "WHERE m.candidate_id=:c AND m.resume_id=:r AND m.prefs_hash=:h AND m.scoring_version=:v AND m.eligible=TRUE AND m.score>=:min "
                        + "ORDER BY m.score DESC, j.retrieved_at DESC LIMIT :l OFFSET :o")
                .param("c", candidateId).param("r", resume.get().id()).param("h", prefsService.hash(prefs))
                .param("v", MatchScorer.SCORING_VERSION).param("min", min).param("l", limit).param("o", offset)
                .query((rs, i) -> map(rs, false)).list();
    }

    public JobView detail(UUID candidateId, UUID jobId) {
        UUID resumeId = resumes.latest(candidateId).map(ResumeService.Current::id).orElse(new UUID(0, 0));
        String hash = prefsService.hash(prefsService.get(candidateId));
        return jdbc.sql(COLS + "FROM job j JOIN company c ON c.id=j.company_id LEFT JOIN job_match m ON m.job_id=j.id AND m.candidate_id=:c "
                        + "AND m.resume_id=:r AND m.prefs_hash=:h AND m.scoring_version=:v WHERE j.id=:id")
                .param("c", candidateId).param("r", resumeId).param("h", hash).param("v", MatchScorer.SCORING_VERSION).param("id", jobId)
                .query((rs, i) -> map(rs, true)).optional().orElseThrow(() -> ApiException.notFound("Job"));
    }

    private JobView map(ResultSet rs, boolean withDescription) throws SQLException {
        rs.getObject("m_score");
        MatchView match = null;
        if (!rs.wasNull()) {
            match = new MatchView(rs.getBoolean("m_eligible"), rs.getInt("m_score"), json.list(rs.getString("matched_skills")),
                    json.list(rs.getString("missing_requirements")), json.list(rs.getString("evidence")),
                    json.list(rs.getString("uncertainty")), json.list(rs.getString("rejection_reasons")), rs.getInt("resume_version"));
        }
        String source = rs.getString("source");
        OffsetDateTime posted = rs.getObject("posted_at", OffsetDateTime.class);
        int reposts = rs.getInt("repost_count");
        return new JobView(rs.getObject("id", UUID.class), source, source.equals("fixture"), rs.getString("title"),
                rs.getString("company_name"), rs.getString("location"), rs.getBoolean("remote"),
                PreferencesService.intOrNull(rs, "salary_min"), PreferencesService.intOrNull(rs, "salary_max"),
                rs.getString("source_url"), rs.getObject("retrieved_at", OffsetDateTime.class), posted, reposts,
                ListingHealth.warnings(posted == null ? null : posted.toInstant(), reposts, java.time.Instant.now()),
                withDescription ? rs.getString("description") : null, match);
    }
}
