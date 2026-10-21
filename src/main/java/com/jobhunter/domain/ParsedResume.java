package com.jobhunter.domain;

import java.util.List;
import java.util.Map;

/** seniority and suggestedRoles are derived deterministically from the resume; they may be null on resumes parsed by older versions. */
public record ParsedResume(List<String> skills, Integer yearsExperience, Map<String, String> sections,
                           String seniority, List<String> suggestedRoles) {}
