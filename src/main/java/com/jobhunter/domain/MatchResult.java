package com.jobhunter.domain;

import java.util.List;

public record MatchResult(int score, List<String> matchedSkills, List<String> missingRequirements,
                          List<String> evidence, List<String> uncertainty) {}
