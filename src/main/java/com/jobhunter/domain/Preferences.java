package com.jobhunter.domain;

import java.util.List;

/** Candidate search preferences. yearsExperience is the candidate's own figure; min/maxYears bound the job's requirement. */
public record Preferences(List<String> targetTitles, List<String> locations, boolean remoteOk, Integer salaryFloor,
                          Integer yearsExperience, Integer minYears, Integer maxYears, int minMatchScore) {}
