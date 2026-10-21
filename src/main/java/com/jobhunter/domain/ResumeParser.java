package com.jobhunter.domain;

import java.util.*;

public final class ResumeParser {
    private static final Set<String> HEADINGS = Set.of("summary", "experience", "work experience",
            "professional experience", "projects", "education", "skills", "technical skills", "certifications");

    private ResumeParser() {}

    public static ParsedResume parse(String text) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = "header";
        for (String line : text.split("\\R")) {
            String h = line.trim().toLowerCase().replaceAll("[:\\s]+$", "");
            if (HEADINGS.contains(h)) { current = h; sections.putIfAbsent(current, new StringBuilder()); continue; }
            sections.computeIfAbsent(current, k -> new StringBuilder()).append(line.trim()).append('\n');
        }
        Map<String, String> out = new LinkedHashMap<>();
        sections.forEach((k, v) -> {
            String s = v.toString().trim();
            if (!s.isEmpty()) out.put(k, s.length() > 1500 ? s.substring(0, 1500) : s);
        });
        return new ParsedResume(new ArrayList<>(SkillExtractor.extract(text)), SkillExtractor.statedYears(text), out);
    }
}
