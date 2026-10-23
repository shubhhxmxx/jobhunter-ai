package com.jobhunter.service;

import com.jobhunter.security.CryptoService;
import com.jobhunter.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {
    public record LoginResult(String apiKey, String name) {}
    public record AccountView(String name, String email, boolean hasPasscode) {}

    private final JdbcClient jdbc;
    private final LoginThrottle throttle;
    private final SecureRandom random = new SecureRandom();

    public AccountService(JdbcClient jdbc, LoginThrottle throttle) {
        this.jdbc = jdbc;
        this.throttle = throttle;
    }

    /** Email + passcode sign-in. Returns a fresh access key for this device; the same error covers unknown email and wrong passcode. */
    @Transactional
    public LoginResult login(String email, String passcode, String ip) {
        String e = email.trim().toLowerCase();
        String emailKey = "e:" + e, ipKey = "i:" + ip;
        if (throttle.blocked(emailKey, 8) || throttle.blocked(ipKey, 30)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many attempts. Try again in 15 minutes");
        }
        record Row(UUID id, String name, String hash) {}
        Optional<Row> row = jdbc.sql("SELECT id,name,passcode_hash FROM candidate_profile WHERE email=:e").param("e", e)
                .query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("passcode_hash"))).optional();
        boolean ok = CryptoService.verifyPasscode(passcode, row.map(Row::hash).orElse(null)) && row.isPresent();
        if (!ok) {
            throttle.fail(emailKey);
            throttle.fail(ipKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or passcode is incorrect");
        }
        throttle.clear(emailKey);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String key = "jh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = row.get().id();
        jdbc.sql("INSERT INTO login_session(id,candidate_id,key_hash,created_at) VALUES(:id,:c,:h,:t)")
                .param("id", UUID.randomUUID()).param("c", id).param("h", CryptoService.sha256Hex(key))
                .param("t", OffsetDateTime.now(ZoneOffset.UTC)).update();
        jdbc.sql("DELETE FROM login_session WHERE candidate_id=:c AND id NOT IN "
                + "(SELECT id FROM login_session WHERE candidate_id=:c ORDER BY created_at DESC LIMIT 10)").param("c", id).update();
        return new LoginResult(key, row.get().name());
    }

    public AccountView view(UUID candidateId) {
        return jdbc.sql("SELECT name,email,passcode_hash FROM candidate_profile WHERE id=:id").param("id", candidateId)
                .query((rs, i) -> new AccountView(rs.getString("name"), rs.getString("email"), rs.getString("passcode_hash") != null)).single();
    }

    public AccountView setPasscode(UUID candidateId, String passcode) {
        jdbc.sql("UPDATE candidate_profile SET passcode_hash=:h WHERE id=:id").param("h", CryptoService.hashPasscode(passcode)).param("id", candidateId).update();
        return view(candidateId);
    }
}
