package com.jobhunter.domain;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Deterministic, dictionary-based skill and years-of-experience extraction. No LLM involved. */
public final class SkillExtractor {
    private static final Map<String, Pattern> SKILLS = new LinkedHashMap<>();

    private static void add(String canonical, String... aliases) {
        String alt = Stream.concat(Stream.of(canonical), Arrays.stream(aliases))
                .map(a -> Pattern.quote(a.toLowerCase())).collect(Collectors.joining("|"));
        SKILLS.put(canonical, Pattern.compile("(?<![a-z0-9+#.])(?:" + alt + ")(?![a-z0-9+#])"));
    }

    static {
        add("Java"); add("Kotlin"); add("Python"); add("Go", "golang"); add("JavaScript"); add("TypeScript");
        add("C#"); add("C++"); add("Rust"); add("Scala");
        add("Spring", "spring boot", "springboot", "spring framework", "spring mvc", "spring data");
        add("Hibernate", "jpa"); add("Kafka", "apache kafka"); add("RabbitMQ"); add("HBase"); add("Cassandra");
        add("PostgreSQL", "postgres"); add("MySQL"); add("MongoDB"); add("Redis");
        add("Elasticsearch", "opensearch"); add("Hadoop"); add("Spark", "apache spark"); add("Flink");
        add("AWS", "amazon web services"); add("GCP", "google cloud"); add("Azure");
        add("Docker"); add("Kubernetes", "k8s"); add("Terraform"); add("Jenkins"); add("CI/CD", "cicd");
        add("Git"); add("REST", "rest api", "rest apis", "restful"); add("GraphQL"); add("gRPC");
        add("Microservices", "microservice"); add("System Design", "distributed systems", "distributed system");
        add("SQL"); add("LLM", "llms", "large language model", "large language models");
        add("RAG", "retrieval augmented generation", "retrieval-augmented generation");
        add("LangChain", "langgraph"); add("React"); add("Node.js", "nodejs"); add("Angular");
        add("Linux"); add("Maven"); add("Gradle"); add("JUnit"); add("Machine Learning");
    }

    private static final Pattern YEARS =
            Pattern.compile("(\\d{1,2})\\s*(?:\\+|-\\s*\\d{1,2}|to\\s*\\d{1,2})?\\s*(?:years?|yrs?)");

    private static final Pattern YEARS_NEAR_EXPERIENCE = Pattern.compile(
            "(\\d{1,2})\\s*(?:\\+|-\\s*\\d{1,2}|to\\s*\\d{1,2})?\\s*(?:years?|yrs?)[^.\\n]{0,60}?experience"
                    + "|experience[^.\\n]{0,60}?(\\d{1,2})\\s*(?:\\+|-\\s*\\d{1,2}|to\\s*\\d{1,2})?\\s*(?:years?|yrs?)");

    private SkillExtractor() {}

    public static Set<String> extract(String text) {
        String t = text == null ? "" : text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        SKILLS.forEach((name, p) -> { if (p.matcher(t).find()) found.add(name); });
        return found;
    }

    /**
     * Years of experience a job asks for. Prefers figures written next to the word "experience" (the largest, since that is
     * the overall requirement); otherwise the smallest "N years" figure. Null if none.
     */
    public static Integer requiredYears(String text) {
        String t = text == null ? "" : text.toLowerCase();
        Integer best = null;
        Matcher near = YEARS_NEAR_EXPERIENCE.matcher(t);
        while (near.find()) {
            int v = Integer.parseInt(near.group(1) != null ? near.group(1) : near.group(2));
            if (v >= 1 && v <= 30 && (best == null || v > best)) best = v;
        }
        if (best != null) return best;
        Integer min = null;
        Matcher m = YEARS.matcher(t);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v >= 1 && v <= 30 && (min == null || v < min)) min = v;
        }
        return min;
    }

    /** Largest "N years" figure in a resume, or null if none. */
    public static Integer statedYears(String text) {
        Integer max = null;
        Matcher m = YEARS.matcher(text == null ? "" : text.toLowerCase());
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v >= 1 && v <= 40 && (max == null || v > max)) max = v;
        }
        return max;
    }

    /** First sentence of the text that mentions the skill, trimmed. Used as match evidence. */
    public static Optional<String> snippet(String text, String skill) {
        for (String s : (text == null ? "" : text).split("(?<=[.!?])\\s+|\\n+")) {
            if (extract(s).contains(skill)) {
                String c = s.trim();
                return Optional.of(c.length() > 160 ? c.substring(0, 157) + "..." : c);
            }
        }
        return Optional.empty();
    }
}
