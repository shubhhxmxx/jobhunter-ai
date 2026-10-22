package com.jobhunter.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class JobNormalizer {
    private JobNormalizer() {}

    public static String stripHtml(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</div>", "\n").replaceAll("<[^>]+>", " ");
        s = decodeNumericEntities(s);
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t\\u00a0]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
    }

    private static final java.util.regex.Pattern NUMERIC_ENTITY = java.util.regex.Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});");

    private static String decodeNumericEntities(String s) {
        java.util.regex.Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String simplify(String s) {
        return (s == null ? "" : s).toLowerCase()
                .replaceAll("back[- ]end", "backend").replaceAll("front[- ]end", "frontend")
                .replaceAll("full[- ]stack", "fullstack");
    }

    /** Titles treat developer, programmer and engineer as the same word, so "Backend Engineer" matches "Backend Developer". */
    private static String canonicalWord(String t) {
        return t.equals("developer") || t.equals("programmer") || t.equals("dev") || t.equals("developers") || t.equals("engineers") ? "engineer" : t;
    }

    /** Lower-case word tokens; keeps + and # so c++ / c# survive. */
    public static Set<String> tokens(String s) {
        return Arrays.stream(simplify(s).split("[^a-z0-9+#]+")).filter(t -> !t.isBlank()).map(JobNormalizer::canonicalWord).collect(Collectors.toSet());
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
