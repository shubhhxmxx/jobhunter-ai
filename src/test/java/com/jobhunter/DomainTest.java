package com.jobhunter;

import com.jobhunter.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    private static final String RESUME = """
            Shubham
            Summary
            Backend engineer with 4+ years of experience building Java Spring Boot microservices.
            Experience
            Built Kafka pipelines on HBase and Redis for high volume invoice processing.
            Education
            B.Tech
            """;

    private static Preferences prefs() {
        return new Preferences(List.of("Backend Engineer", "Java Developer"), List.of("Bengaluru"), true,
                100_000, null, 2, 6, 60);
    }

    private static JobPosting job(String title, String loc, boolean remote, Integer salaryMax, String desc) {
        return new JobPosting("t", "1", title, "Acme", loc, remote, null, salaryMax, desc, "https://example.invalid/1");
    }

    @Test void parsesSkillsYearsAndSections() {
        ParsedResume r = ResumeParser.parse(RESUME);
        assertTrue(r.skills().containsAll(List.of("Java", "Spring", "Kafka", "HBase", "Redis", "Microservices")));
        assertEquals(4, r.yearsExperience());
        assertTrue(r.sections().containsKey("experience"));
        assertTrue(r.sections().containsKey("education"));
    }

    @Test void javaDoesNotMatchJavascript() {
        assertFalse(SkillExtractor.extract("We use JavaScript and TypeScript").contains("Java"));
        assertTrue(SkillExtractor.extract("We use Java, Kafka.").contains("Kafka"));
    }

    @Test void filtersByTitleLocationSalaryAndExperience() {
        Preferences p = prefs();
        assertTrue(EligibilityFilter.reasons(job("Senior Back-end Engineer", "Bengaluru, India", false, null, "3+ years"), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Frontend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Pune", false, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Germany", true, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Bengaluru", false, 90_000, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Bengaluru", false, null, "10+ years of experience"), p).isEmpty());
    }

    @Test void unknownSalaryAndLocationDoNotRejectJobs() {
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", null, false, null, "no details"), prefs()).isEmpty());
    }

    @Test void scoreIsExplainableAndBounded() {
        ParsedResume r = ResumeParser.parse(RESUME);
        JobPosting good = job("Backend Engineer", "Bengaluru", false, null,
                "Build services with Java and Spring Boot. You know Kafka and Docker. 3+ years of experience.");
        MatchResult m = MatchScorer.score(good, r, prefs());
        assertTrue(m.matchedSkills().containsAll(List.of("Java", "Spring", "Kafka")));
        assertEquals(List.of("Docker"), m.missingRequirements());
        assertFalse(m.evidence().isEmpty());
        assertTrue(m.score() > 60 && m.score() <= 100, "score " + m.score());
    }

    @Test void betterSkillOverlapScoresHigher() {
        ParsedResume r = ResumeParser.parse(RESUME);
        MatchResult a = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, "Java Kafka Spring. 3 years"), r, prefs());
        MatchResult b = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, "Python Django AWS. 3 years"), r, prefs());
        assertTrue(a.score() > b.score());
    }

    @Test void emptyDescriptionIsFlaggedAsUncertain() {
        MatchResult m = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, ""), ResumeParser.parse(RESUME), prefs());
        assertFalse(m.uncertainty().isEmpty());
    }

    @Test void dedupKeyIgnoresCaseAndPunctuationAndSource() {
        assertEquals(JobNormalizer.dedupKey("Acme, Inc.", "Back-end Engineer", "Bengaluru", false),
                JobNormalizer.dedupKey("ACME Inc", "Backend engineer", "bengaluru", false));
    }

    @Test void stripsHtml() {
        assertEquals("Hello & world\nNext", JobNormalizer.stripHtml("<p>Hello &amp; <b>world</b></p><p>Next</p>"));
    }
}
