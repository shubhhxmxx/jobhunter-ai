package com.jobhunter.domain;

import java.util.List;

/** openToIndia: also accept other Indian cities (flagged as relocation). openToAbroad: also accept jobs abroad that state visa sponsorship. abroadWithoutVisa: also show jobs abroad that do not mention a visa (flagged, ranked lower); a posting that says it will not sponsor is never shown. excludedLevels are keys from EligibilityFilter.LEVELS (staff, principal, ...) whose titles are skipped. salaryFloor is in LPA (lakh INR per year). yearsExperience is the candidate's own figure and only affects scoring. */
public record Preferences(List<String> targetTitles, List<String> locations, boolean remoteOk, Integer salaryFloor,
                          Integer yearsExperience, List<String> excludedLevels, boolean openToIndia, boolean openToAbroad,
                          boolean abroadWithoutVisa, int minMatchScore) {}
