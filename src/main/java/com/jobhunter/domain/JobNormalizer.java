package com.jobhunter.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class JobNormalizer {
    private JobNormalizer() {}

    public static String stripHtml(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</div>", "\n").replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t\\u00a0]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
    }

    private static String simplify(String s) {
        return (s == null ? "" : s).toLowerCase()
                .replaceAll("back[- ]end", "backend").replaceAll("front[- ]end", "frontend")
                .replaceAll("full[- ]stack", "fullstack");
    }

    /** Lower-case word tokens; keeps + and # so c++ / c# survive. */
    public static Set<String> tokens(String s) {
        return Arrays.stream(simplify(s).split("[^a-z0-9+#]+")).filter(t -> !t.isBlank()).collect(Collectors.toSet());
    }

    public static boolean titleMatches(String jobTitle, Iterable<String> targets) {
        Set<String> jt = tokens(jobTitle);
        for (String t : targets) {
            Set<String> tt = tokens(t);
            if (!tt.isEmpty() && jt.containsAll(tt)) return true;
        }
        return false;
    }

    public static String key(String s) {
        return simplify(s).replaceAll("[^a-z0-9]", "");
    }

    /** Same company + title + place collapses to one job, whichever source listed it. */
    public static String dedupKey(String company, String title, String location, boolean remote) {
        return key(company) + "|" + key(title) + "|" + (remote ? "remote" : key(location));
    }
}
