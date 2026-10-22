package com.jobhunter.domain;

import java.util.List;

/** excludedLevels are keys from EligibilityFilter.LEVELS (staff, principal, ...) whose titles are skipped. salaryFloor is in LPA (lakh INR per year). yearsExperience is the candidate's own figure and only affects scoring. */
public record Preferences(List<String> targetTitles, List<String> locations, boolean remoteOk, Integer salaryFloor,
                          Integer yearsExperience, List<String> excludedLevels, int minMatchScore) {}
