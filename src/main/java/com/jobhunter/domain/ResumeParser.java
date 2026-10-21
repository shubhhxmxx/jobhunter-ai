package com.jobhunter.domain;

import java.time.YearMonth;
import java.util.*;

public final class ResumeParser {
    private static final Set<String> HEADINGS = Set.of("summary", "experience", "work experience",
            "professional experience", "projects", "education", "skills", "technical skills", "certifications");

    private ResumeParser() {}

    public static ParsedResume parse(String text) {
        return parse(text, YearMonth.now());
    }

    public static ParsedResume parse(String text, YearMonth now) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = "header";
        for (String line : text.split("\\R")) {
            String h = line.trim().toLowerCase().replaceAll("[:\\s]+$", "");
            if (HEADINGS.contains(h)) { current = h; sections.putIfAbsent(current, new StringBuilder()); continue; }
            sections.computeIfAbsent(current, k -> new StringBuilder()).append(line.trim()).append('\n');
        }
        StringBuilder work = new StringBuilder();
        for (String k : List.of("experience", "work experience", "professional experience")) {
            if (sections.containsKey(k)) work.append(sections.get(k)).append('\n');
        }
        Integer dated = ExperienceEstimator.estimateYears(work.length() > 0 ? work.toString() : text, now);
        Integer years = dated != null && dated > 0 ? dated : SkillExtractor.statedYears(text);

        Map<String, String> out = new LinkedHashMap<>();
        sections.forEach((k, v) -> {
            String s = v.toString().trim();
            if (!s.isEmpty()) out.put(k, s.length() > 1500 ? s.substring(0, 1500) : s);
        });
        List<String> skills = new ArrayList<>(SkillExtractor.extract(text));
        return new ParsedResume(skills, years, out, RoleSuggester.seniority(years), RoleSuggester.suggest(skills));
    }
}
