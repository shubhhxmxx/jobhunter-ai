package com.jobhunter.domain;

import java.util.*;

/**
 * Hard, deterministic rules. A job that fails any of them is never shown. Runs before scoring and never calls an LLM.
 * Unknown data passes, except where the rule is about being close to you (location, visa, skills).
 */
public final class EligibilityFilter {
    /** Seniority levels a candidate can exclude, with the title words that identify each. */
    public static final Map<String, Set<String>> LEVELS = Map.of(
            "intern", Set.of("intern", "internship", "trainee", "werkstudent", "praktikum"),
            "junior", Set.of("junior", "jr", "graduate", "entry"),
            "senior", Set.of("senior", "sr"),
            "lead", Set.of("lead", "head"),
            "staff", Set.of("staff"),
            "principal", Set.of("principal"),
            "manager", Set.of("manager"),
            "director", Set.of("director", "vp"),
            "architect", Set.of("architect"));

    /** Required experience must be within this many years of the candidate's own. */
    public static final int EXPERIENCE_BAND = 1;

    private EligibilityFilter() {}

    /** Returns the reasons a job is ineligible, each starting with a [category] tag; an empty list means eligible. */
    public static List<String> reasons(JobPosting j, Preferences p, Candidate c) {
        List<String> r = new ArrayList<>();
        if (!p.targetTitles().isEmpty() && !JobNormalizer.titleMatches(j.title(), p.targetTitles())) {
            r.add("[title] Title does not match any target title");
        }
        r.addAll(OffProfile.reasons(j.title(), c, p.targetTitles()));

        Integer mine = p.yearsExperience() != null ? p.yearsExperience() : c.years();
        Set<String> excluded = new LinkedHashSet<>(p.excludedLevels());
        if (mine != null) { // levels that cannot fit your experience are always skipped
            if (mine < 5) excluded.addAll(List.of("staff", "principal", "lead", "manager", "director", "architect"));
            if (mine >= 2) excluded.add("intern");
            if (mine >= 3) excluded.add("junior");
        }
        Set<String> titleWords = JobNormalizer.tokens(j.title());
        for (String level : excluded) {
            Set<String> words = LEVELS.get(level);
            if (words != null && words.stream().anyMatch(titleWords::contains)) r.add("[level] Seniority level '" + level + "' is excluded");
        }

        LocationInfo li = LocationInfo.classify(j, p);
        if (!li.eligible()) r.add(li.rejection());

        // Posting salaries are LPA and only known when the listing states one in INR. Unknown passes.
        if (p.salaryFloor() != null && j.salaryMax() != null && j.salaryMax() < p.salaryFloor()) {
            r.add("[salary] Top of salary range (" + j.salaryMax() + " LPA) is below your floor (" + p.salaryFloor() + " LPA)");
        }

        Integer req = SkillExtractor.requiredYears(j.description());
        if (mine != null && req != null && Math.abs(req - mine) > EXPERIENCE_BAND) {
            r.add("[years] Asks for " + req + "+ years; you have about " + mine + " (accepted: " + (mine - EXPERIENCE_BAND) + " to " + (mine + EXPERIENCE_BAND) + ")");
        }

        if (!c.skills().isEmpty()) {
            Set<String> required = SkillExtractor.extract(j.title() + "\n" + j.description());
            long matched = required.stream().filter(c.skills()::contains).count();
            int n = required.size();
            if ((n >= 3 && matched < Math.ceil(0.4 * n)) || (n >= 1 && n <= 2 && matched == 0)) {
                r.add("[skills] Only " + matched + " of " + n + " required skills are on your resume");
            }
        }
        return r;
    }
}
