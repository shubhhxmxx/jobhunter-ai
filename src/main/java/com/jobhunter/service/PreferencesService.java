package com.jobhunter.service;

import com.jobhunter.domain.Preferences;
import com.jobhunter.security.CryptoService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PreferencesService {
    private final JdbcClient jdbc;
    private final Json json;

    public PreferencesService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Preferences get(UUID candidateId) {
        return jdbc.sql("SELECT * FROM candidate_preferences WHERE candidate_id=:id").param("id", candidateId)
                .query((rs, i) -> new Preferences(json.list(rs.getString("target_titles")), json.list(rs.getString("locations")),
                        rs.getBoolean("remote_ok"), intOrNull(rs, "salary_floor"), intOrNull(rs, "years_experience"),
                        rs.getInt("min_match_score")))
                .single();
    }

    public void update(UUID candidateId, List<String> titles, List<String> locations, boolean remoteOk, Integer salaryFloor,
                       Integer yearsExperience) {
        jdbc.sql("UPDATE candidate_preferences SET target_titles=:tt, locations=:lo, remote_ok=:r, salary_floor=:sf, "
                        + "years_experience=:ye, updated_at=:t WHERE candidate_id=:id")
                .param("tt", json.write(titles)).param("lo", json.write(locations)).param("r", remoteOk)
                .param("sf", salaryFloor).param("ye", yearsExperience)
                .param("t", OffsetDateTime.now(ZoneOffset.UTC)).param("id", candidateId).update();
    }

    public void setThreshold(UUID candidateId, int score) {
        jdbc.sql("UPDATE candidate_preferences SET min_match_score=:s, updated_at=:t WHERE candidate_id=:id")
                .param("s", score).param("t", OffsetDateTime.now(ZoneOffset.UTC)).param("id", candidateId).update();
    }

    /** Hash of everything that changes eligibility or score. The threshold is excluded so changing it never forces a re-score. */
    public String hash(Preferences p) {
        return CryptoService.sha256Hex(json.write(new Object[]{p.targetTitles(), p.locations(), p.remoteOk(),
                p.salaryFloor(), p.yearsExperience()}));
    }

    static Integer intOrNull(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
