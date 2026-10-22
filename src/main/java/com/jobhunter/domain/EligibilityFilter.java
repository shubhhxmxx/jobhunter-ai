package com.jobhunter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Hard, deterministic eligibility rules. Runs before scoring and never calls an LLM. Unknown data passes. */
public final class EligibilityFilter {
    private static final Pattern OPEN_REMOTE = Pattern.compile(
            "worldwide|anywhere|global|everywhere|^\\s*remote\\b(?!.*(\\(|\\bonly\\b|\\bus\\b|\\busa\\b|\\beu\\b|\\buk\\b|\\beurope\\b|\\bcanada\\b))");
    private static final Set<String> INDIAN_CITIES = Set.of("bengaluru", "bangalore", "mumbai", "delhi", "new delhi", "ncr", "gurgaon",
            "gurugram", "noida", "hyderabad", "pune", "chennai", "kolkata", "ahmedabad");

    private EligibilityFilter() {}

    /** Target locations plus obvious aliases: Bengaluru/Bangalore, and "india" for any Indian city. */
    static Set<String> expand(List<String> targets) {
        Set<String> out = new HashSet<>();
        for (String t : targets) {
            String l = t.toLowerCase(Locale.ROOT).trim();
            out.add(l);
            if (l.equals("bengaluru")) out.add("bangalore");
            if (l.equals("bangalore")) out.add("bengaluru");
            if (INDIAN_CITIES.contains(l)) out.add("india");
        }
        return out;
    }

    /** Returns the reasons a job is ineligible; an empty list means eligible. */
    public static List<String> reasons(JobPosting j, Preferences p) {
        List<String> r = new ArrayList<>();
        if (!p.targetTitles().isEmpty() && !JobNormalizer.titleMatches(j.title(), p.targetTitles())) {
            r.add("Title does not match any target title");
        }
        if (!p.locations().isEmpty()) {
            String loc = j.location() == null ? "" : j.location().toLowerCase(Locale.ROOT).trim();
            boolean place = expand(p.locations()).stream().anyMatch(loc::contains);
            boolean open = loc.isBlank() || OPEN_REMOTE.matcher(loc).find(); // Worldwide, Anywhere, plain "Remote"
            // A remote job still has to be open to you: worldwide, or restricted to somewhere you listed.
            boolean ok = place || (j.remote() ? p.remoteOk() && open : loc.isBlank());
            if (!ok) {
                r.add((j.remote() ? "Remote role limited to '" : "Location '") + j.location() + "', outside your target locations");
            }
        } else if (j.remote() && !p.remoteOk()) {
            r.add("Remote role but remote is switched off");
        }
        // Posting salaries are LPA and only known when the listing states one in INR. Unknown passes.
        if (p.salaryFloor() != null && j.salaryMax() != null && j.salaryMax() < p.salaryFloor()) {
            r.add("Top of salary range (" + j.salaryMax() + " LPA) is below your floor (" + p.salaryFloor() + " LPA)");
        }
        return r;
    }
}
