package com.jobhunter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Authenticates /api/v1/** with an X-API-Key header; only the key's SHA-256 is stored. Sets the candidateId request attribute. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private final JdbcClient jdbc;

    public ApiKeyFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/v1/") || (p.equals("/api/v1/setup") && req.getMethod().equals("POST"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getHeader("X-API-Key");
        Optional<UUID> id = (key == null || key.isBlank()) ? Optional.empty()
                : jdbc.sql("SELECT id FROM candidate_profile WHERE api_key_hash = :h")
                        .param("h", CryptoService.sha256Hex(key.trim())).query(UUID.class).optional();
        if (id.isEmpty()) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-API-Key header\"}");
            return;
        }
        req.setAttribute("candidateId", id.get());
        chain.doFilter(req, res);
    }
}
