package com.jobhunter.domain;

import java.util.List;
import java.util.Map;

public record ParsedResume(List<String> skills, Integer yearsExperience, Map<String, String> sections) {}
