package com.jobhunter.domain;

import java.util.List;

/** workMode/workLabel/relocation/visa describe where the job is relative to the candidate (see LocationInfo). */
public record MatchResult(int score, List<String> matchedSkills, List<String> missingRequirements, List<String> evidence,
                          List<String> uncertainty, String workMode, String workLabel, boolean relocation, String visa) {}
