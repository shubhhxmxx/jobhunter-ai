package com.jobhunter.web;

import com.jobhunter.service.CandidateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SetupController {
    public record SetupRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Size(min = 6, max = 100) String passcode) {
        @Override
        public String toString() { // keep the passcode out of logs
            return "SetupRequest[name=" + name + ", email=" + email + ", passcode=****]";
        }
    }
    public record SetupResponse(UUID candidateId, String apiKey, String note) {}

    private final CandidateService candidates;

    public SetupController(CandidateService candidates) {
        this.candidates = candidates;
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public SetupResponse setup(@Valid @RequestBody SetupRequest req) {
        CandidateService.Created c = candidates.setup(req.name(), req.email(), req.passcode());
        return new SetupResponse(c.candidateId(), c.apiKey(), "This access key is shown once. Next time, sign in with your email and passcode.");
    }
}
