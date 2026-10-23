package com.jobhunter.domain;

import java.util.*;
import java.util.function.BiPredicate;

/** Drops roles from other disciplines (UI, test/QA, embedded, mobile...) unless the candidate asked for them or has the skills. */
public final class OffProfile {
    private record Rule(String name, Set<String> words, BiPredicate<Candidate, Set<String>> allowed) {}

    private static final Set<String> FRONT_SKILLS = Set.of("React", "Angular", "TypeScript", "JavaScript");
    private static final List<Rule> RULES = List.of(
            new Rule("test or QA", Set.of("test", "tester", "testing", "qa", "sdet", "quality"), (c, t) -> false),
            new Rule("front-end or UI", Set.of("frontend", "ui", "ux", "react", "angular", "vue"),
                    (c, t) -> c.skills().stream().filter(FRONT_SKILLS::contains).count() >= 2),
            new Rule("embedded or hardware", Set.of("embedded", "firmware", "fpga", "hardware", "automotive", "rtos", "vlsi", "asic", "kernel"), (c, t) -> false),
            new Rule("C++", Set.of("c++"), (c, t) -> c.skills().contains("C++")),
            new Rule("mobile", Set.of("ios", "android", "mobile", "flutter", "swift"), (c, t) -> false),
            new Rule("data science or research", Set.of("scientist", "researcher"), (c, t) -> false),
            new Rule("sales, support or other non-engineering", Set.of("sales", "marketing", "recruiter", "recruiting", "designer",
                    "accountant", "support", "customer", "writer", "copywriter"), (c, t) -> false));

    private OffProfile() {}

    public static List<String> reasons(String title, Candidate c, List<String> targetTitles) {
        Set<String> titleWords = JobNormalizer.tokens(title);
        Set<String> targetWords = new HashSet<>();
        for (String t : targetTitles) targetWords.addAll(JobNormalizer.tokens(t));
        List<String> out = new ArrayList<>();
        for (Rule r : RULES) {
            boolean hit = r.words().stream().anyMatch(titleWords::contains);
            boolean asked = r.words().stream().anyMatch(targetWords::contains);
            if (hit && !asked && !r.allowed().test(c, targetWords)) out.add("[role] " + r.name() + " roles are outside your profile");
        }
        return out;
    }
}
