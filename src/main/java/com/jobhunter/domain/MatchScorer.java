package com.jobhunter.domain;

import java.util.*;

/**
 * Explainable 0-100 ranking score. Every job scored has already passed the hard filters, so it starts at 10. Then skills 45 (full marks at 6 matched skills,
 * so long skill lists are not punished), title 15, experience 20, and location +5 same city, +10 visa-sponsored abroad, -10 abroad with no visa mention.
 * Pure and deterministic, so results can be cached against (resume version, preferences, SCORING_VERSION).
 */
public final class MatchScorer {
    /** Part of the cache key for stored results. Bump it whenever EligibilityFilter or scoring logic changes, or old results stay cached. */
    public static final String SCORING_VERSION = "v5";
    private static final int PASSED_FILTERS = 10;

    private MatchScorer() {}

    public static MatchResult score(JobPosting job, ParsedResume resume, Preferences prefs) {
        List<String> uncertainty = new ArrayList<>();
        String jobText = job.title() + "\n" + job.description();
        Set<String> required = SkillExtractor.extract(jobText);
        Set<String> have = new HashSet<>(resume.skills());

        List<String> matched = required.stream().filter(have::contains).toList();
        List<String> missing = required.stream().filter(s -> !have.contains(s)).toList();

        int skillPts;
        if (required.isEmpty()) {
            skillPts = 22;
            uncertainty.add("No recognizable skills in the description, so skills were scored neutrally");
        } else {
            skillPts = (int) Math.round(45.0 * Math.min(1.0, matched.size() / (double) Math.min(required.size(), 6)));
        }

        int titlePts;
        if (prefs.targetTitles().isEmpty()) {
            titlePts = 8;
            uncertainty.add("No target titles set, so title fit was scored neutrally");
        } else {
            titlePts = JobNormalizer.titleMatches(job.title(), prefs.targetTitles()) ? 15 : 0;
        }

        Integer req = SkillExtractor.requiredYears(job.description());
        Integer mine = prefs.yearsExperience() != null ? prefs.yearsExperience() : resume.yearsExperience();
        int expPts;
        int penalty = 0;
        if (req == null) {
            expPts = 10;
            uncertainty.add("No experience requirement stated in the posting");
        } else if (mine == null) {
            expPts = 10;
            uncertainty.add("Your years of experience are unknown; set yearsExperience in preferences");
        } else {
            int gap = req - mine;
            if (gap <= 0) expPts = 20;
            else if (gap == 1) expPts = 15;
            else if (gap == 2) expPts = 8;
            else { expPts = 0; penalty = Math.min(15, 5 * (gap - 2)); } // 3+ years short: rank clearly lower
            if (gap >= 2) uncertainty.add("Asks for " + req + "+ years of experience; you have about " + mine);
        }

        List<String> evidence = new ArrayList<>();
        for (String s : matched.stream().limit(5).toList()) {
            SkillExtractor.snippet(job.description(), s).ifPresent(sn -> evidence.add(s + ": \"" + sn + "\""));
        }
        // Jobs in your own city and abroad jobs that sponsor a visa get a boost.
        LocationInfo where = LocationInfo.classify(job, prefs);
        int bonus = switch (where.mode()) { case "abroad_visa" -> 10; case "same_city" -> 5; case "abroad_no_visa" -> -10; default -> 0; };
        int total = Math.max(0, Math.min(100, PASSED_FILTERS + skillPts + titlePts + expPts - penalty + bonus));
        return new MatchResult(total, matched, missing, evidence, uncertainty, where.mode(), where.label(), where.relocation(), where.visa().name());
    }
}
