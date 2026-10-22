package com.jobhunter.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hard, deterministic eligibility rules. Runs before scoring and never calls an LLM. Unknown data passes. */
public final class EligibilityFilter {
    private EligibilityFilter() {}

    /** Returns the reasons a job is ineligible; an empty list means eligible. */
    public static List<String> reasons(JobPosting j, Preferences p) {
        List<String> r = new ArrayList<>();
        if (!p.targetTitles().isEmpty() && !JobNormalizer.titleMatches(j.title(), p.targetTitles())) {
            r.add("Title does not match any target title");
        }
        if (!p.locations().isEmpty()) {
            boolean remoteOk = j.remote() && p.remoteOk();
            String loc = j.location() == null ? "" : j.location().toLowerCase(Locale.ROOT);
            boolean placeOk = loc.isBlank() || p.locations().stream().anyMatch(l -> loc.contains(l.toLowerCase(Locale.ROOT)));
            if (!remoteOk && !placeOk) r.add("Location '" + j.location() + "' is outside your target locations");
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
