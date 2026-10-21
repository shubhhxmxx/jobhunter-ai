package com.jobhunter.service;

import com.jobhunter.security.CryptoService;
import com.jobhunter.web.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
public class CandidateService {
    public record Created(UUID candidateId, String apiKey) {}

    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    public CandidateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the profile with default preferences. The raw API key is returned once and only its hash is stored. */
    @Transactional
    public Created setup(String name, String email) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String key = "jh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            jdbc.sql("INSERT INTO candidate_profile(id,name,email,api_key_hash,created_at) VALUES(:id,:n,:e,:h,:t)")
                    .param("id", id).param("n", name.trim()).param("e", email.trim().toLowerCase())
                    .param("h", CryptoService.sha256Hex(key)).param("t", now).update();
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "A profile with this email already exists");
        }
        jdbc.sql("INSERT INTO candidate_preferences(candidate_id,target_titles,locations,remote_ok,min_match_score,updated_at) "
                        + "VALUES(:id,'[]','[]',TRUE,60,:t)").param("id", id).param("t", now).update();
        return new Created(id, key);
    }
}
