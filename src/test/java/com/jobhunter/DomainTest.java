package com.jobhunter;

import com.jobhunter.domain.*;
import com.jobhunter.source.HnPostParser;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
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
                10, null, 60);
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
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Worldwide", true, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Bengaluru", false, 8, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Bengaluru", false, 12, ""), p).isEmpty());
        // years required by the job is not a filter
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Bengaluru", false, null, "10+ years of experience"), p).isEmpty());
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

    @Test void estimatesYearsFromDateRangesAndIgnoresEducation() {
        String cv = "Experience\nEngineer, Infosys Aug 2023 - Dec 2025\nEngineer, Flipkart Mar 2026 - Present\n"
                + "Education\nB.Tech Aug 2019 - May 2023\n";
        ParsedResume r = ResumeParser.parse(cv, YearMonth.of(2026, 10));
        assertEquals(3, r.yearsExperience());
        assertEquals("Mid-level", r.seniority());
    }

    @Test void overlappingRangesAreMerged() {
        assertEquals(2, ExperienceEstimator.estimateYears("Jan 2023 - Dec 2023 and Jun 2023 - Dec 2024", YearMonth.of(2026, 1)));
    }

    @Test void suggestsRolesFromSkills() {
        ParsedResume r = ResumeParser.parse(RESUME);
        assertTrue(r.suggestedRoles().contains("Backend Engineer"));
        assertTrue(r.suggestedRoles().contains("Java Developer"));
        assertFalse(r.suggestedRoles().contains("Frontend Engineer"));
        assertTrue(RoleSuggester.suggest(List.of()).isEmpty());
    }

    @Test void parsesLpaSalaries() {
        assertEquals(List.of(20, 30), java.util.Arrays.stream(SalaryParser.parseLpa("Pay: 20-30 LPA")).boxed().toList());
        assertEquals(List.of(25, 25), java.util.Arrays.stream(SalaryParser.parseLpa("\u20b925 LPA fixed")).boxed().toList());
        assertEquals(List.of(12, 13), java.util.Arrays.stream(SalaryParser.parseLpa("12.5 lakhs per annum")).boxed().toList());
        assertEquals(null, SalaryParser.parseLpa("$150k-$200k"));
    }

    @Test void parsesHackerNewsHiringPosts() {
        JobPosting p = HnPostParser.parse("Resend (YC W23) | Senior Backend Engineer | Remote (US) | $150k<p>We build email APIs with TypeScript and Postgres.", "42");
        assertEquals("Resend (YC W23)", p.company());
        assertEquals("Senior Backend Engineer", p.title());
        assertTrue(p.remote());
        assertEquals("https://news.ycombinator.com/item?id=42", p.url());
        assertTrue(p.description().contains("TypeScript"));
        assertEquals(null, HnPostParser.parse("Just a general comment, no header here", "43"));
        assertFalse(HnPostParser.parse("Acme | Backend Engineer | Onsite only, Berlin", "44").remote());
    }

    @Test void decodesHtmlEntitiesInTitles() {
        assertEquals("Python & Cloud Native Engineer's", JobNormalizer.stripHtml("Python &amp; Cloud Native Engineer&#39;s"));
        assertEquals("It's", JobNormalizer.stripHtml("It&#x27;s"));
    }

    @Test void remoteJobsMustBeOpenToYourLocation() {
        Preferences p = prefs(); // Bengaluru, remote ok
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Z\u00fcrich", true, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Germany", true, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "USA Only", true, null, ""), p).isEmpty());
        assertFalse(EligibilityFilter.reasons(job("Backend Engineer", "Remote (US)", true, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Worldwide", true, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Remote", true, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "India", true, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", "Bangalore, KA", false, null, ""), p).isEmpty());
        assertTrue(EligibilityFilter.reasons(job("Backend Engineer", null, true, null, ""), p).isEmpty());
    }
}
