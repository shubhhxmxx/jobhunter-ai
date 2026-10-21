package com.jobhunter.web;

import com.jobhunter.service.JobQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobQueryService jobs;

    public JobController(JobQueryService jobs) {
        this.jobs = jobs;
    }

    /** Ranked, eligible jobs. minScore defaults to the configured threshold. */
    @GetMapping
    public List<JobQueryService.JobView> list(@RequestAttribute("candidateId") UUID id,
                                              @RequestParam(required = false) Integer minScore,
                                              @RequestParam(defaultValue = "50") int limit,
                                              @RequestParam(defaultValue = "0") int offset) {
        return jobs.ranked(id, minScore, Math.max(1, Math.min(limit, 200)), Math.max(0, offset));
    }

    @GetMapping("/{id}")
    public JobQueryService.JobView get(@RequestAttribute("candidateId") UUID candidateId, @PathVariable UUID id) {
        return jobs.detail(candidateId, id);
    }

    // Later milestones. Honest 501s rather than fake data.
    @PostMapping("/{id}/contacts/search")
    public void searchContacts(@PathVariable UUID id) {
        throw notImplemented("Contact discovery");
    }

    @GetMapping("/{id}/contacts")
    public void contacts(@PathVariable UUID id) {
        throw notImplemented("Contact discovery");
    }

    @PostMapping("/{id}/outreach-drafts")
    public void outreach(@PathVariable UUID id) {
        throw notImplemented("Outreach drafting");
    }

    private static ApiException notImplemented(String what) {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", what + " is not implemented yet (planned for a later milestone)");
    }
}
