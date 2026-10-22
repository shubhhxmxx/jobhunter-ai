package com.jobhunter.service;

import com.jobhunter.domain.JobNormalizer;
import com.jobhunter.domain.JobPosting;
import com.jobhunter.domain.SalaryParser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobRepository {
    public record Row(UUID id, JobPosting posting) {}

    private final JdbcClient jdbc;

    public JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the posting, or refreshes retrieved_at if the same job (by source id or normalized company/title/place) exists. */
    public UUID upsert(JobPosting p) {
        // Sources send HTML entities such as &amp; in titles; decode before storing and before building the dedup key.
        String title = JobNormalizer.stripHtml(p.title());
        String companyName = JobNormalizer.stripHtml(p.company());
        String place = p.location() == null ? null : JobNormalizer.stripHtml(p.location());
        String key = JobNormalizer.dedupKey(companyName, title, place, p.remote());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<UUID> existing = find(p, key);
        if (existing.isPresent()) {
            jdbc.sql("UPDATE job SET retrieved_at=:t, status='OPEN', title=:ti, location=:lo, posted_at=COALESCE(posted_at,:pa), "
                            + "repost_count=CASE WHEN :rc > repost_count THEN :rc ELSE repost_count END WHERE id=:id")
                    .param("t", now).param("ti", truncate(title, 500)).param("lo", truncate(place, 300))
                    .param("pa", postedAt(p)).param("rc", p.repostCount()).param("id", existing.get()).update();
            return existing.get();
        }
        UUID id = UUID.randomUUID();
        String desc = JobNormalizer.stripHtml(p.description());
        if (desc.length() > 20000) desc = desc.substring(0, 20000);
        int[] sal = (p.salaryMin() == null && p.salaryMax() == null) ? SalaryParser.parseLpa(title + " " + desc) : null;
        Integer salMin = p.salaryMin();
        Integer salMax = p.salaryMax();
        if (sal != null) { salMin = sal[0]; salMax = sal[1]; } // a ternary here would unbox a null Integer and throw
        try {
            jdbc.sql("INSERT INTO job(id,source,external_id,company_id,title,location,remote,salary_min,salary_max,description,source_url,dedup_key,status,retrieved_at,posted_at,repost_count) "
                            + "VALUES(:id,:s,:x,:c,:ti,:lo,:r,:smin,:smax,:d,:u,:k,'OPEN',:t,:pa,:rc)")
                    .param("id", id).param("s", p.source()).param("x", p.externalId()).param("c", company(companyName))
                    .param("ti", truncate(title, 500)).param("lo", truncate(place, 300)).param("r", p.remote())
                    .param("smin", salMin).param("smax", salMax).param("d", desc).param("u", truncate(p.url(), 2000))
                    .param("k", truncate(key, 900)).param("t", now)
                    .param("pa", postedAt(p)).param("rc", p.repostCount()).update();
            return id;
        } catch (DuplicateKeyException e) {
            return find(p, key).orElseThrow(() -> e);
        }
    }

    private static OffsetDateTime postedAt(JobPosting p) {
        return p.postedAt() == null ? null : p.postedAt().atOffset(ZoneOffset.UTC);
    }

    private Optional<UUID> find(JobPosting p, String key) {
        Optional<UUID> byKey = jdbc.sql("SELECT id FROM job WHERE dedup_key=:k").param("k", truncate(key, 900)).query(UUID.class).optional();
        if (byKey.isPresent()) return byKey;
        return jdbc.sql("SELECT id FROM job WHERE source=:s AND external_id=:x").param("s", p.source()).param("x", p.externalId())
                .query(UUID.class).optional();
    }

    private UUID company(String name) {
        String key = JobNormalizer.key(name);
        if (key.isEmpty()) key = "unknown";
        final String k = truncate(key, 300);
        Optional<UUID> id = jdbc.sql("SELECT id FROM company WHERE name_key=:k").param("k", k).query(UUID.class).optional();
        if (id.isPresent()) return id.get();
        try {
            UUID n = UUID.randomUUID();
            jdbc.sql("INSERT INTO company(id,name,name_key) VALUES(:id,:n,:k)").param("id", n).param("n", truncate(name, 300)).param("k", k).update();
            return n;
        } catch (DuplicateKeyException e) {
            return jdbc.sql("SELECT id FROM company WHERE name_key=:k").param("k", k).query(UUID.class).single();
        }
    }

    public List<Row> openJobs(int limit) {
        return jdbc.sql("SELECT j.id,j.source,j.external_id,j.title,j.location,j.remote,j.salary_min,j.salary_max,j.description,j.source_url,c.name AS company_name "
                        + "FROM job j JOIN company c ON c.id=j.company_id WHERE j.status='OPEN' ORDER BY j.retrieved_at DESC LIMIT :l")
                .param("l", limit)
                .query((rs, i) -> new Row(rs.getObject("id", UUID.class), new JobPosting(rs.getString("source"), rs.getString("external_id"),
                        rs.getString("title"), rs.getString("company_name"), rs.getString("location"), rs.getBoolean("remote"),
                        PreferencesService.intOrNull(rs, "salary_min"), PreferencesService.intOrNull(rs, "salary_max"),
                        rs.getString("description"), rs.getString("source_url"), null, 0)))
                .list();
    }

    private static String truncate(String s, int n) {
        return s == null ? null : (s.length() > n ? s.substring(0, n) : s);
    }
}
