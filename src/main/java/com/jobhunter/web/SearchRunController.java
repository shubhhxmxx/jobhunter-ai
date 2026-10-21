package com.jobhunter.web;

import com.jobhunter.service.SearchRunService;
import com.jobhunter.service.SearchRunWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search-runs")
public class SearchRunController {
    private final SearchRunService runs;
    private final SearchRunWorker worker;

    public SearchRunController(SearchRunService runs, SearchRunWorker worker) {
        this.runs = runs;
        this.worker = worker;
    }

    /** Returns 202 immediately; poll the Location URL for status. */
    @PostMapping
    public ResponseEntity<SearchRunService.RunView> start(@RequestAttribute("candidateId") UUID candidateId) {
        UUID id = runs.create(candidateId);
        worker.execute(id);
        return ResponseEntity.accepted().location(URI.create("/api/v1/search-runs/" + id)).body(runs.view(candidateId, id));
    }

    @GetMapping("/{id}")
    public SearchRunService.RunView get(@RequestAttribute("candidateId") UUID candidateId, @PathVariable UUID id) {
        return runs.view(candidateId, id);
    }
}
