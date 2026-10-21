package com.jobhunter.source;

import com.jobhunter.domain.JobPosting;

import java.util.List;

public interface JobSource {
    record Query(List<String> titles, List<String> locations, boolean remoteOk) {}

    String id();

    /** Fetch current listings. Must only return real postings with a real source URL. */
    List<JobPosting> fetch(Query query) throws Exception;
}
