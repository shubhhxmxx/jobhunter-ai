package com.jobhunter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowTest {
    private static final String KEY = "X-API-Key";
    private static final String SECRET = "sk-test-SECRET-1234567890";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcClient jdbc;

    private static byte[] pdf(String... lines) throws Exception {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            d.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(d, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(14);
                cs.newLineAtOffset(40, 740);
                for (String l : lines) { cs.showText(l); cs.newLine(); }
                cs.endText();
            }
            d.save(out);
            return out.toByteArray();
        }
    }

    private JsonNode json(String body) throws Exception { return om.readTree(body); }

    @Test
    void endToEndFlow() throws Exception {
        // setup + auth
        String apiKey = json(mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test User\",\"email\":\"t@example.com\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("apiKey").asText();
        mvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/setup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"not-an-email\"}")).andExpect(status().isBadRequest());

        // a search needs a resume and target titles first
        mvc.perform(post("/api/v1/search-runs").header(KEY, apiKey)).andExpect(status().isConflict());

        // resume validation and versioning
        mvc.perform(multipart("/api/v1/resumes").file(new MockMultipartFile("file", "cv.txt", "text/plain", "hi".getBytes()))
                .header(KEY, apiKey)).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/v1/resumes").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "not a pdf".getBytes()))
                .header(KEY, apiKey)).andExpect(status().isUnsupportedMediaType());
        byte[] cv = pdf("Backend Engineer", "Summary", "4+ years building Java Spring Boot microservices.",
                "Experience", "Kafka, HBase, Redis and PostgreSQL pipelines at scale.", "Education", "B.Tech");
        for (int v = 1; v <= 2; v++) {
            JsonNode r = json(mvc.perform(multipart("/api/v1/resumes").file(new MockMultipartFile("file", "cv.pdf", "application/pdf", cv))
                    .header(KEY, apiKey)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            assertEquals(v, r.get("version").asInt());
            assertTrue(r.get("parsed").get("skills").toString().contains("Kafka"));
        }
        assertEquals(2, json(mvc.perform(get("/api/v1/resumes").header(KEY, apiKey)).andReturn().getResponse().getContentAsString()).size());

        // preferences + threshold
        mvc.perform(put("/api/v1/preferences").header(KEY, apiKey).contentType(MediaType.APPLICATION_JSON).content("""
                {"targetTitles":["Backend Engineer","Java Developer"],"locations":["Bengaluru"],"remoteOk":true,
                 "salaryFloor":10,"yearsExperience":4,"excludedLevels":["staff","principal"]}""")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/settings/matching").header(KEY, apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"minMatchScore\":101}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/settings/matching").header(KEY, apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"minMatchScore\":40}")).andExpect(status().isOk());

        // BYOK: the raw key is never returned and is encrypted at rest
        String llm = mvc.perform(put("/api/v1/settings/llm").header(KEY, apiKey).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"anthropic\",\"model\":\"claude-sonnet-4-5\",\"apiKey\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(llm.contains(SECRET));
        assertFalse(mvc.perform(get("/api/v1/settings/llm").header(KEY, apiKey)).andReturn().getResponse().getContentAsString().contains(SECRET));
        String stored = jdbc.sql("SELECT encrypted_key FROM provider_credentials").query(String.class).single();
        assertFalse(stored.contains(SECRET));

        // async search run: 202 + status endpoint
        var started = mvc.perform(post("/api/v1/search-runs").header(KEY, apiKey)).andExpect(status().isAccepted()).andReturn().getResponse();
        String runId = json(started.getContentAsString()).get("id").asText();
        assertTrue(started.getHeader("Location").endsWith(runId));
        JsonNode run = null;
        for (int i = 0; i < 100; i++) {
            run = json(mvc.perform(get("/api/v1/search-runs/" + runId).header(KEY, apiKey)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if (!run.get("status").asText().equals("QUEUED") && !run.get("status").asText().equals("RUNNING")) break;
            Thread.sleep(100);
        }
        assertEquals("COMPLETED", run.get("status").asText());
        assertEquals(7, run.get("jobsFound").asInt(), "8 fixtures, one is a cross-board duplicate");

        // ranked output: hard filters applied, scores descending, fixtures labelled
        JsonNode jobs = json(mvc.perform(get("/api/v1/jobs").header(KEY, apiKey)).andReturn().getResponse().getContentAsString());
        assertEquals(3, jobs.size(), "fixtures 1, 2 and 6 pass; fixture 5 is a Staff role");
        assertTrue(jobs.get(0).get("match").get("score").asInt() >= jobs.get(1).get("match").get("score").asInt());
        assertTrue(jobs.get(0).get("fixture").asBoolean());
        assertTrue(jobs.get(0).get("url").asText().contains("example.invalid"));
        assertFalse(jobs.get(0).get("match").get("matchedSkills").isEmpty());

        // threshold change needs no re-scoring
        int rowsBefore = jdbc.sql("SELECT COUNT(*) FROM job_match").query(Integer.class).single();
        assertEquals(1, json(mvc.perform(get("/api/v1/jobs?minScore=85").header(KEY, apiKey)).andReturn().getResponse().getContentAsString()).size());

        // a second run reuses cached scores
        mvc.perform(post("/api/v1/search-runs").header(KEY, apiKey)).andExpect(status().isAccepted());
        Thread.sleep(1500);
        assertEquals(rowsBefore, jdbc.sql("SELECT COUNT(*) FROM job_match").query(Integer.class).single());

        // detail + later-milestone endpoints
        String jobId = jobs.get(0).get("id").asText();
        mvc.perform(get("/api/v1/jobs/" + jobId).header(KEY, apiKey)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/00000000-0000-0000-0000-000000000001").header(KEY, apiKey)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/jobs/" + jobId + "/outreach-drafts").header(KEY, apiKey)).andExpect(status().isNotImplemented());
    }
}
