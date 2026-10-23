package com.jobhunter.domain;

import java.util.Set;

/** What the filters need to know about the candidate: years of experience (may be unknown) and skills found on the resume. */
public record Candidate(Integer years, Set<String> skills) {}
