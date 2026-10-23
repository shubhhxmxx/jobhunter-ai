package com.jobhunter.domain;

import com.jobhunter.domain.VisaDetector.Visa;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Where the job is relative to the candidate, and whether it is acceptable.
 * Home is India when any target location is an Indian city: same city, other Indian cities (flagged as relocation) and
 * worldwide-remote roles are acceptable; anything based abroad needs explicit visa sponsorship in the posting.
 * mode is one of: same_city, india_other_city, remote_india, remote_worldwide, abroad_visa, unspecified, rejected.
 */
public record LocationInfo(String mode, String label, boolean relocation, Visa visa, boolean eligible, String rejection) {
    static final Set<String> INDIA_PLACES = Set.of("india", "bengaluru", "bangalore", "mumbai", "delhi", "gurgaon", "gurugram",
            "noida", "hyderabad", "pune", "chennai", "kolkata", "ahmedabad", "kochi", "jaipur", "chandigarh", "coimbatore",
            "indore", "trivandrum", "thiruvananthapuram", "mysuru", "mysore", "nagpur", "bhubaneswar", "vadodara", "surat",
            "lucknow", "visakhapatnam", "mohali", "karnataka", "maharashtra", "telangana", "tamil nadu", "haryana",
            "uttar pradesh", "kerala", "gujarat", "west bengal", "ncr");
    private static final Pattern OPEN = Pattern.compile("worldwide|anywhere|global|everywhere|\\basia\\b|\\bapac\\b"
            + "|^\\s*remote\\b(?!.*(\\(|\\bonly\\b|\\bus\\b|\\busa\\b|\\beu\\b|\\buk\\b|\\beurope\\b|\\bcanada\\b))");

    /** Target locations plus aliases (Bengaluru/Bangalore). */
    static Set<String> cities(List<String> targets) {
        Set<String> out = new HashSet<>();
        for (String t : targets) {
            String l = t.toLowerCase(Locale.ROOT).trim();
            out.add(l);
            if (l.equals("bengaluru")) out.add("bangalore");
            if (l.equals("bangalore")) out.add("bengaluru");
        }
        return out;
    }

    private static LocationInfo no(Visa v, String reason) {
        return new LocationInfo("rejected", "", false, v, false, reason);
    }

    public static LocationInfo classify(JobPosting j, Preferences p) {
        Visa visa = VisaDetector.detect(j.title() + " " + j.description());
        String raw = j.location() == null ? "" : j.location().trim();
        String loc = raw.toLowerCase(Locale.ROOT);
        if (p.locations().isEmpty()) {
            if (j.remote() && !p.remoteOk()) return no(visa, "[remote] Remote role but remote is switched off");
            return new LocationInfo("unspecified", j.remote() ? "Remote" : raw.isEmpty() ? "Location not listed" : raw, false, visa, true, null);
        }
        Set<String> cities = cities(p.locations());
        boolean homeIndia = cities.stream().anyMatch(INDIA_PLACES::contains);
        boolean sameCity = cities.stream().filter(c -> !c.equals("india")).anyMatch(loc::contains);
        boolean inIndia = sameCity || INDIA_PLACES.stream().anyMatch(loc::contains);
        boolean open = loc.isBlank() || OPEN.matcher(loc).find();

        if (j.remote() && !p.remoteOk() && !sameCity) return no(visa, "[remote] Remote role but remote is switched off");
        if (sameCity) return new LocationInfo("same_city", (j.remote() ? "Remote or hybrid, " : "On-site, ") + "in your city (" + raw + ")", false, visa, true, null);
        if (!homeIndia) { // no country rules for other home countries: only your cities and open remote roles
            if (j.remote() && open) return new LocationInfo("remote_worldwide", "Remote, open worldwide", false, visa, true, null);
            return no(visa, "[location] '" + raw + "' is outside your target locations");
        }
        if (j.remote()) {
            if (inIndia) return new LocationInfo("remote_india", "Remote within India (" + raw + ")", false, visa, true, null);
            if (open) return new LocationInfo("remote_worldwide", raw.isBlank() ? "Remote (region not stated)" : "Remote, open worldwide", false, visa, true, null);
        } else {
            if (inIndia) {
                if (p.openToIndia()) return new LocationInfo("india_other_city", "On-site in " + raw + ". You would need to relocate within India", true, visa, true, null);
                return no(visa, "[location] '" + raw + "' is another Indian city");
            }
            if (loc.isBlank()) return no(visa, "[location] Location not listed");
        }
        // Based abroad (on-site, or remote but restricted to residents of that place): needs explicit visa sponsorship.
        if (visa == Visa.YES && p.openToAbroad()) {
            String where = j.remote() ? "Remote, but you must live in " + raw : "On-site in " + raw;
            return new LocationInfo("abroad_visa", where + ". Visa sponsorship or relocation support is offered", true, visa, true, null);
        }
        return no(visa, "[visa] Based in '" + raw + "' and " + (visa == Visa.NO ? "says it will not sponsor a visa" : "no visa sponsorship is stated"));
    }
}
