package com.jobhunter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** When demo fixtures are switched off, delete the fixture jobs (and their matches) left over from earlier runs. */
@Component
@ConditionalOnProperty(name = "jobhunter.sources.fixtures-enabled", havingValue = "false")
public class FixtureCleanup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FixtureCleanup.class);
    private final JdbcClient jdbc;

    public FixtureCleanup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.sql("DELETE FROM job_match WHERE job_id IN (SELECT id FROM job WHERE source='fixture')").update();
            int n = jdbc.sql("DELETE FROM job WHERE source='fixture'").update();
            if (n > 0) log.info("Removed {} fixture jobs", n);
        } catch (Exception e) {
            log.warn("Fixture cleanup skipped: {}", e.getClass().getSimpleName());
        }
    }
}
