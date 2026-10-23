package com.jobhunter;

import com.jobhunter.domain.*;
import com.jobhunter.source.HnPostParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private static final Candidate CAND = new Candidate(4, new java.util.HashSet<>(ResumeParser.parse(RESUME).skills()));

    private static List<String> reasons(JobPosting j, Preferences p) {
        return EligibilityFilter.reasons(j, p, CAND);
    }

    private static Preferences prefs() {
        return new Preferences(List.of("Backend Engineer", "Java Developer"), List.of("Bengaluru"), true,
                10, null, List.of(), true, true, 60);
    }

    private static JobPosting job(String title, String loc, boolean remote, Integer salaryMax, String desc) {
        return new JobPosting("t", "1", title, "Acme", loc, remote, null, salaryMax, desc, "https://example.invalid/1", null, 0);
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
        assertTrue(reasons(job("Senior Back-end Engineer", "Bengaluru, India", false, null, "3+ years"), p).isEmpty());
        assertFalse(reasons(job("Frontend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Pune", false, null, ""), p).isEmpty()); // other Indian city: shown, flagged as relocation
        assertTrue(reasons(job("Backend Engineer", "Worldwide", true, null, ""), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, 8, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, 12, ""), p).isEmpty());
        // experience far above yours is a hard filter
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "10+ years of experience"), p).isEmpty());
    }

    @Test void unknownSalaryPassesButUnknownLocationDoesNot() {
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "no details"), prefs()).isEmpty());
        assertFalse(reasons(job("Backend Engineer", null, false, null, "no details"), prefs()).isEmpty());
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
        // header that runs straight into the intro paragraph, as in real posts
        JobPosting e = HnPostParser.parse("Enveritas (YC S18, non-profit) | Backend Software Engineer | Remote (Global) | "
                + "<a href=\"https://enveritas.org/jobs/\">https://enveritas.org/jobs/</a> Enveritas is a 501(c)(3) nonprofit working on sustainability issues facing smallholder coffee farmers.<p>Python, Docker, AWS", "45");
        assertEquals("Enveritas (YC S18, non-profit)", e.company());
        assertEquals("Backend Software Engineer", e.title());
        assertEquals("Remote (Global)", e.location());
        assertEquals("https://enveritas.org/jobs/", e.url());
        assertTrue(e.remote());
        assertFalse(HnPostParser.parse("Acme | Backend Engineer | Onsite only, Berlin", "44").remote());
    }

    @Test void decodesHtmlEntitiesInTitles() {
        assertEquals("Python & Cloud Native Engineer's", JobNormalizer.stripHtml("Python &amp; Cloud Native Engineer&#39;s"));
        assertEquals("It's", JobNormalizer.stripHtml("It&#x27;s"));
    }

    @Test void remoteJobsMustBeOpenToYourLocation() {
        Preferences p = prefs(); // Bengaluru, remote ok
        assertFalse(reasons(job("Backend Engineer", "Z\u00fcrich", true, null, ""), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Germany", true, null, ""), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "USA Only", true, null, ""), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Remote (US)", true, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Worldwide", true, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Remote", true, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "India", true, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bangalore, KA", false, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", null, true, null, ""), p).isEmpty());
    }

    @Test void skipsExcludedSeniorityLevels() {
        Preferences p = new Preferences(List.of("Backend Engineer"), List.of("Bengaluru"), true, null, null, List.of("staff", "principal"), true, true, 60);
        assertFalse(reasons(job("Staff Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertFalse(reasons(job("Principal Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertTrue(reasons(job("Senior Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "Our staff are great"), p).isEmpty());
    }

    @Test void jobsAskingFarMoreYearsRankClearlyLower() {
        ParsedResume r = ResumeParser.parse(RESUME); // about 4 years
        Preferences p = new Preferences(List.of("Backend Engineer"), List.of("Bengaluru"), true, null, 3, List.of(), true, true, 60);
        String skills = "Java Spring Kafka Redis. ";
        MatchResult fits = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, skills + "2+ years"), r, p);
        MatchResult stretch = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, skills + "4+ years"), r, p);
        MatchResult far = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, skills + "8+ years"), r, p);
        assertTrue(fits.score() > stretch.score() && stretch.score() > far.score(), fits.score() + " " + stretch.score() + " " + far.score());
        assertTrue(fits.score() - far.score() >= 25, "gap " + (fits.score() - far.score()));
        assertTrue(far.uncertainty().stream().anyMatch(u -> u.contains("8+ years") && u.contains("about 3")));
        assertTrue(fits.uncertainty().stream().noneMatch(u -> u.contains("Asks for")));
    }

    @Test void warnsAboutOldAndRepostedListings() {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        assertTrue(ListingHealth.warnings(now.minusSeconds(10L * 86400), 0, now).isEmpty());
        assertEquals(1, ListingHealth.warnings(now.minusSeconds(60L * 86400), 0, now).size());
        assertEquals(1, ListingHealth.warnings(null, 2, now).size());
        assertTrue(ListingHealth.warnings(null, 1, now).isEmpty());
        assertTrue(ListingHealth.warnings(null, 0, now).isEmpty());
    }

    @Test void developerAndEngineerTitlesMatch() {
        assertTrue(JobNormalizer.titleMatches("Senior Backend Developer", List.of("Backend Engineer")));
        assertTrue(JobNormalizer.titleMatches("Java Engineer", List.of("Java Developer")));
        assertFalse(JobNormalizer.titleMatches("Frontend Developer", List.of("Backend Engineer")));
    }

    private static Preferences india(boolean otherCities, boolean abroad) {
        return new Preferences(List.of("Backend Engineer", "Software Engineer"), List.of("Bengaluru"), true, null, null,
                List.of(), otherCities, abroad, 60);
    }

    @Test void otherIndianCitiesAreFlaggedAsRelocation() {
        JobPosting pune = job("Backend Engineer", "Pune, Maharashtra", false, null, "");
        assertTrue(reasons(pune, india(true, true)).isEmpty());
        LocationInfo li = LocationInfo.classify(pune, india(true, true));
        assertEquals("india_other_city", li.mode());
        assertTrue(li.relocation());
        assertTrue(li.label().contains("relocate"));
        assertFalse(reasons(pune, india(false, true)).isEmpty());
        assertFalse(LocationInfo.classify(job("Backend Engineer", "Bengaluru", false, null, ""), india(true, true)).relocation());
    }

    @Test void abroadNeedsExplicitVisaSponsorship() {
        Preferences p = india(true, true);
        assertFalse(reasons(job("Backend Engineer", "Berlin, Germany", false, null, "Great team."), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Berlin, Germany", false, null, "No visa sponsorship available."), p).isEmpty());
        JobPosting sponsored = job("Backend Engineer", "Berlin, Germany", false, null, "We offer visa sponsorship and relocation support.");
        assertTrue(reasons(sponsored, p).isEmpty());
        assertEquals("abroad_visa", LocationInfo.classify(sponsored, p).mode());
        assertTrue(LocationInfo.classify(sponsored, p).relocation());
        assertFalse(reasons(sponsored, india(true, false)).isEmpty());
        JobPosting remoteUs = job("Backend Engineer", "USA Only", true, null, "We sponsor work visas.");
        assertTrue(reasons(remoteUs, p).isEmpty());
        assertTrue(LocationInfo.classify(remoteUs, p).label().contains("must live in"));
        assertFalse(reasons(job("Backend Engineer", "USA Only", true, null, "Great team."), p).isEmpty());
    }

    @Test void visaSponsoringAndLocalJobsRankHigher() {
        ParsedResume r = ResumeParser.parse(RESUME);
        String d = "Java Spring Kafka Redis. 4+ years of experience. ";
        int abroadVisa = MatchScorer.score(job("Backend Engineer", "Berlin, Germany", false, null, d + "We offer visa sponsorship."), r, india(true, true)).score();
        int worldwide = MatchScorer.score(job("Backend Engineer", "Worldwide", true, null, d), r, india(true, true)).score();
        int local = MatchScorer.score(job("Backend Engineer", "Bengaluru", false, null, d), r, india(true, true)).score();
        assertTrue(abroadVisa > worldwide, abroadVisa + " vs " + worldwide);
        assertTrue(local < abroadVisa + 1 && local > worldwide, local + "");
    }

    @Test void detectsVisaWording() {
        assertEquals(VisaDetector.Visa.YES, VisaDetector.detect("We provide visa sponsorship"));
        assertEquals(VisaDetector.Visa.YES, VisaDetector.detect("Relocation assistance included"));
        assertEquals(VisaDetector.Visa.NO, VisaDetector.detect("We do not offer visa sponsorship"));
        assertEquals(VisaDetector.Visa.NO, VisaDetector.detect("Unable to sponsor. Must be authorized to work in the US"));
        assertEquals(VisaDetector.Visa.UNKNOWN, VisaDetector.detect("Great team, free lunch"));
    }

    @Test void experienceMustBeWithinOneYearOfYours() { // candidate has 4 years
        Preferences p = india(true, true);
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "3+ years of experience"), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "5+ years of experience"), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "6+ years of experience"), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "2+ years of experience"), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "No years stated"), p).isEmpty());
        // the overall requirement wins over a smaller per-skill figure
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "2+ years Kafka experience. 7+ years of professional experience as a software engineer."), p).isEmpty());
        assertEquals(7, SkillExtractor.requiredYears("7+ years of professional experience as a software engineer, led projects (roughly 3+ months)"));
    }

    @Test void offProfileRolesAreDropped() {
        Preferences p = india(true, true);
        for (String t : List.of("UI Software Engineer", "Software Engineer in Test", "Embedded Software Engineer (C++)",
                "Android Software Engineer", "Software Engineer, QA Automation"))
            assertFalse(reasons(job(t, "Bengaluru", false, null, ""), p).isEmpty(), t);
        assertTrue(reasons(job("Software Engineer, Backend", "Bengaluru", false, null, ""), p).isEmpty());
        // allowed when you asked for it
        Preferences wantsUi = new Preferences(List.of("UI Engineer"), List.of("Bengaluru"), true, null, null, List.of(), true, true, 60);
        assertTrue(reasons(job("UI Engineer", "Bengaluru", false, null, ""), wantsUi).isEmpty());
    }

    @Test void jobsFarFromYourResumeAreDropped() {
        Preferences p = india(true, true);
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "Python Django AWS Docker Kubernetes"), p).isEmpty());
        assertTrue(reasons(job("Backend Engineer", "Bengaluru", false, null, "Java Spring Kafka Docker"), p).isEmpty());
        assertFalse(reasons(job("Backend Engineer", "Bengaluru", false, null, "Rust and Go"), p).isEmpty());
    }

    @Test void levelsThatCannotFitYourExperienceAreAlwaysSkipped() {
        Preferences p = india(true, true); // no manual exclusions
        assertFalse(reasons(job("Principal Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertFalse(reasons(job("Lead Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertFalse(reasons(job("Junior Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
        assertTrue(reasons(job("Senior Backend Engineer", "Bengaluru", false, null, ""), p).isEmpty());
    }
}
