package com.jobhunter.domain;

import java.util.*;

/** Rule-based role suggestions from the skills found on a resume. Deterministic; no LLM. */
public final class RoleSuggester {
    private record Role(String title, Set<String> skills, int min, String must) {}

    private static final Set<String> BACKEND = Set.of("Java", "Spring", "Kotlin", "Go", "Python", "Node.js", "Microservices",
            "REST", "Kafka", "PostgreSQL", "MySQL", "Redis", "SQL", "gRPC", "Hibernate", "RabbitMQ", "MongoDB");
    private static final Set<String> FRONTEND = Set.of("React", "Angular", "TypeScript", "JavaScript");
    private static final List<Role> ROLES = List.of(
            new Role("Backend Engineer", BACKEND, 3, null),
            new Role("Java Developer", Set.of("Java", "Spring", "Hibernate", "Maven", "Gradle", "JUnit"), 2, "Java"),
            new Role("Data Engineer", Set.of("Spark", "Hadoop", "HBase", "Kafka", "Flink", "Cassandra", "SQL", "Elasticsearch"), 3, null),
            new Role("DevOps Engineer", Set.of("Docker", "Kubernetes", "Terraform", "Jenkins", "CI/CD", "AWS", "GCP", "Azure", "Linux"), 3, null),
            new Role("AI Engineer", Set.of("LLM", "RAG", "LangChain", "Machine Learning"), 2, null),
            new Role("Frontend Engineer", FRONTEND, 2, null));

    private RoleSuggester() {}

    public static List<String> suggest(Collection<String> skills) {
        Set<String> have = new HashSet<>(skills);
        Map<String, Integer> scored = new LinkedHashMap<>();
        for (Role r : ROLES) {
            long hits = r.skills().stream().filter(have::contains).count();
            if (hits >= r.min() && (r.must() == null || have.contains(r.must()))) scored.put(r.title(), (int) hits);
        }
        long backend = BACKEND.stream().filter(have::contains).count();
        long frontend = FRONTEND.stream().filter(have::contains).count();
        if (backend >= 3 && frontend >= 1) scored.put("Full Stack Engineer", (int) (backend + frontend) - 1);
        if (have.contains("Machine Learning")) scored.put("Machine Learning Engineer", 2);
        List<String> out = new ArrayList<>(scored.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue()).map(Map.Entry::getKey).limit(5).toList());
        if (!out.isEmpty()) out.add("Software Engineer");
        return out;
    }

    public static String seniority(Integer years) {
        if (years == null) return null;
        if (years < 2) return "Junior";
        if (years < 5) return "Mid-level";
        if (years < 8) return "Senior";
        return "Staff / Lead";
    }
}
