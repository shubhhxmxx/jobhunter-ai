package com.jobhunter.service;

import com.jobhunter.llm.LlmProvider;
import com.jobhunter.security.CryptoService;
import com.jobhunter.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LlmSettingsService {
    public record View(boolean configured, String provider, String model, String keyHint, List<String> supportedProviders) {}

    private final JdbcClient jdbc;
    private final CryptoService crypto;
    private final List<LlmProvider> providers;

    public LlmSettingsService(JdbcClient jdbc, CryptoService crypto, List<LlmProvider> providers) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.providers = providers;
    }

    @Transactional
    public View save(UUID candidateId, String provider, String model, String apiKey) {
        if (providers.stream().noneMatch(p -> p.id().equals(provider))) {
            throw ApiException.bad("Unsupported provider. Supported: " + supported());
        }
        jdbc.sql("DELETE FROM provider_credentials WHERE candidate_id=:c").param("c", candidateId).update();
        String key = apiKey.trim();
        jdbc.sql("INSERT INTO provider_credentials(id,candidate_id,provider,model,encrypted_key,key_hint,created_at) VALUES(:id,:c,:p,:m,:k,:h,:t)")
                .param("id", UUID.randomUUID()).param("c", candidateId).param("p", provider).param("m", model.trim())
                .param("k", crypto.encrypt(key)).param("h", key.substring(key.length() - 4))
                .param("t", OffsetDateTime.now(ZoneOffset.UTC)).update();
        return get(candidateId);
    }

    /** Never returns the key itself, only the last four characters. */
    public View get(UUID candidateId) {
        Optional<View> v = jdbc.sql("SELECT provider,model,key_hint FROM provider_credentials WHERE candidate_id=:c").param("c", candidateId)
                .query((rs, i) -> new View(true, rs.getString("provider"), rs.getString("model"), "..." + rs.getString("key_hint"), supported()))
                .optional();
        return v.orElse(new View(false, null, null, null, supported()));
    }

    private List<String> supported() {
        return providers.stream().map(LlmProvider::id).toList();
    }
}
