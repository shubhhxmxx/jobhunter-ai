package com.jobhunter.web;

import com.jobhunter.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 100) String passcode) {
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", passcode=****]";
        }
    }

    public record PasscodeRequest(@NotBlank @Size(min = 6, max = 100) String passcode) {
        @Override
        public String toString() {
            return "PasscodeRequest[passcode=****]";
        }
    }

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/login")
    public AccountService.LoginResult login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return accounts.login(req.email(), req.passcode(), clientIp(http));
    }

    @GetMapping("/account")
    public AccountService.AccountView account(@RequestAttribute("candidateId") UUID id) {
        return accounts.view(id);
    }

    @PutMapping("/account/passcode")
    public AccountService.AccountView setPasscode(@RequestAttribute("candidateId") UUID id, @Valid @RequestBody PasscodeRequest req) {
        return accounts.setPasscode(id, req.passcode());
    }

    /** Behind Render's proxy the last X-Forwarded-For entry is the address the proxy itself saw; earlier entries can be forged. */
    private static String clientIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return r.getRemoteAddr();
    }
}
