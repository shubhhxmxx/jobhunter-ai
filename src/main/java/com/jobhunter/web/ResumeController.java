package com.jobhunter.web;

import com.jobhunter.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeService.ResumeView upload(@RequestAttribute("candidateId") UUID candidateId, @RequestPart("file") MultipartFile file) {
        return resumes.upload(candidateId, file);
    }

    @GetMapping
    public List<ResumeService.ResumeView> list(@RequestAttribute("candidateId") UUID candidateId) {
        return resumes.list(candidateId);
    }
}
