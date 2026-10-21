package com.jobhunter.domain;

import java.util.*;

/**
 * Explainable 0-100 score: skills overlap (60) + title fit (20) + experience fit (20).
 * Pure and deterministic, so results can be cached against (resume version, preferences, SCORING_VERSION).
 */
public final class MatchScorer {
    public static final String SCORING_VERSION = "v1";

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
            skillPts = 30;
            uncertainty.add("No recognizable skills in the description, so skills were scored neutrally");
        } else {
            skillPts = (int) Math.round(60.0 * matched.size() / required.size());
        }

        int titlePts;
        if (prefs.targetTitles().isEmpty()) {
            titlePts = 10;
            uncertainty.add("No target titles set, so title fit was scored neutrally");
        } else {
            titlePts = JobNormalizer.titleMatches(job.title(), prefs.targetTitles()) ? 20 : 0;
        }

        Integer req = SkillExtractor.requiredYears(job.description());
        Integer mine = prefs.yearsExperience() != null ? prefs.yearsExperience() : resume.yearsExperience();
        int expPts;
        if (req == null) {
            expPts = 10;
            uncertainty.add("No experience requirement stated in the posting");
        } else if (mine == null) {
            expPts = 10;
            uncertainty.add("Your years of experience are unknown; set yearsExperience in preferences");
        } else {
            expPts = mine >= req ? 20 : (int) Math.round(20.0 * mine / req);
        }

        List<String> evidence = new ArrayList<>();
        for (String s : matched.stream().limit(5).toList()) {
            SkillExtractor.snippet(job.description(), s).ifPresent(sn -> evidence.add(s + ": \"" + sn + "\""));
        }
        int total = Math.max(0, Math.min(100, skillPts + titlePts + expPts));
        return new MatchResult(total, matched, missing, evidence, uncertainty);
    }
}
