package com.jobhunter.domain;

import java.time.Instant;

/**
 * A normalized job listing as returned by a source adapter. salaryMin/Max are INR LPA, or null if unknown or not INR.
 * postedAt is the source's own posting date when it gives one. repostCount is how many earlier monthly threads
 * (Hacker News) already listed the same company and role.
 */
public record JobPosting(String source, String externalId, String title, String company, String location,
                         boolean remote, Integer salaryMin, Integer salaryMax, String description, String url,
                         Instant postedAt, int repostCount) {
    public JobPosting withListingInfo(Instant postedAt, int repostCount) {
        return new JobPosting(source, externalId, title, company, location, remote, salaryMin, salaryMax, description, url,
                postedAt, repostCount);
    }
}
