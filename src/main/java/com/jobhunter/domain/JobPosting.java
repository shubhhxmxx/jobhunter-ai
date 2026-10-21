package com.jobhunter.domain;

/** A normalized job listing as returned by a source adapter. */
public record JobPosting(String source, String externalId, String title, String company, String location,
                         boolean remote, Integer salaryMin, Integer salaryMax, String description, String url) {}
