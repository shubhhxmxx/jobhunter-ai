package com.jobhunter.web;

import com.jobhunter.domain.Preferences;
import com.jobhunter.service.LlmSettingsService;
import com.jobhunter.service.PreferencesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SettingsController {
    public record PreferencesRequest(
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 100) String> targetTitles,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> locations,
            Boolean remoteOk,
            @PositiveOrZero @Max(500) Integer salaryFloor,
            @Min(0) @Max(50) Integer yearsExperience,
            @Size(max = 10) List<@NotBlank String> excludedLevels) {}

    public record MatchingRequest(@NotNull @Min(0) @Max(100) Integer minMatchScore) {}

    public record LlmRequest(@NotBlank String provider, @NotBlank @Size(max = 100) String model,
                             @NotBlank @Size(min = 8, max = 500) String apiKey) {
        @Override
        public String toString() { // keep the key out of logs
            return "LlmRequest[provider=" + provider + ", model=" + model + ", apiKey=****]";
        }
    }

    private final PreferencesService prefs;
    private final LlmSettingsService llm;

    public SettingsController(PreferencesService prefs, LlmSettingsService llm) {
        this.prefs = prefs;
        this.llm = llm;
    }

    @GetMapping("/preferences")
    public Preferences getPreferences(@RequestAttribute("candidateId") UUID id) {
        return prefs.get(id);
    }

    @PutMapping("/preferences")
    public Preferences putPreferences(@RequestAttribute("candidateId") UUID id, @Valid @RequestBody PreferencesRequest r) {
        List<String> levels = r.excludedLevels() == null ? List.of() : r.excludedLevels().stream().map(l -> l.trim().toLowerCase()).toList();
        for (String l : levels) {
            if (!com.jobhunter.domain.EligibilityFilter.LEVELS.containsKey(l)) {
                throw ApiException.bad("Unknown level '" + l + "'. Allowed: " + new java.util.TreeSet<>(com.jobhunter.domain.EligibilityFilter.LEVELS.keySet()));
            }
        }
        prefs.update(id, r.targetTitles().stream().map(String::trim).toList(),
                r.locations() == null ? List.of() : r.locations().stream().map(String::trim).toList(),
                r.remoteOk() == null || r.remoteOk(), r.salaryFloor(), r.yearsExperience(), levels);
        return prefs.get(id);
    }

    @PutMapping("/settings/matching")
    public Preferences putMatching(@RequestAttribute("candidateId") UUID id, @Valid @RequestBody MatchingRequest r) {
        prefs.setThreshold(id, r.minMatchScore());
        return prefs.get(id);
    }

    @GetMapping("/settings/llm")
    public LlmSettingsService.View getLlm(@RequestAttribute("candidateId") UUID id) {
        return llm.get(id);
    }

    @PutMapping("/settings/llm")
    public LlmSettingsService.View putLlm(@RequestAttribute("candidateId") UUID id, @Valid @RequestBody LlmRequest r) {
        return llm.save(id, r.provider().trim().toLowerCase(), r.model(), r.apiKey());
    }
}
