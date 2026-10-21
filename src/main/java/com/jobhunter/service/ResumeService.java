package com.jobhunter.service;

import com.jobhunter.domain.ParsedResume;
import com.jobhunter.domain.ResumeParser;
import com.jobhunter.security.CryptoService;
import com.jobhunter.web.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeService {
    public record ResumeView(UUID id, int version, String filename, long sizeBytes, OffsetDateTime createdAt, ParsedResume parsed) {}
    public record Current(UUID id, int version, ParsedResume parsed) {}

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final JdbcClient jdbc;
    private final Json json;

    public ResumeService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public ResumeView upload(UUID candidateId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.bad("Resume file is empty");
        if (file.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File exceeds the 5 MB limit");
        String name = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._ -]", "_");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!name.toLowerCase().endsWith(".pdf") || !(type.equals("application/pdf") || type.equals("application/octet-stream"))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE", "Only PDF resumes are accepted");
        }
        byte[] bytes;
        String text;
        try {
            bytes = file.getBytes();
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE", "File content is not a PDF");
            }
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                if (doc.getNumberOfPages() > 15) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_PAGES", "Resume has more than 15 pages");
                text = new PDFTextStripper().getText(doc);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_PDF", "PDF could not be read (encrypted or corrupt)");
        }
        if (text.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_TEXT", "No extractable text. Scanned PDFs are not supported yet");

        ParsedResume parsed = ResumeParser.parse(text);
        int version = jdbc.sql("SELECT COALESCE(MAX(version),0)+1 FROM resume WHERE candidate_id=:c").param("c", candidateId)
                .query(Integer.class).single();
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO resume(id,candidate_id,version,filename,content_type,size_bytes,sha256,text_content,parsed_json,created_at) "
                        + "VALUES(:id,:c,:v,:f,'application/pdf',:s,:h,:tx,:p,:t)")
                .param("id", id).param("c", candidateId).param("v", version).param("f", name).param("s", (long) bytes.length)
                .param("h", CryptoService.sha256Hex(new String(bytes, StandardCharsets.ISO_8859_1))).param("tx", text)
                .param("p", json.write(parsed)).param("t", now).update();
        return new ResumeView(id, version, name, bytes.length, now, parsed);
    }

    public List<ResumeView> list(UUID candidateId) {
        return jdbc.sql("SELECT id,version,filename,size_bytes,created_at,parsed_json FROM resume WHERE candidate_id=:c ORDER BY version DESC")
                .param("c", candidateId)
                .query((rs, i) -> new ResumeView(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("filename"),
                        rs.getLong("size_bytes"), rs.getObject("created_at", OffsetDateTime.class),
                        json.read(rs.getString("parsed_json"), new com.fasterxml.jackson.core.type.TypeReference<ParsedResume>() {})))
                .list();
    }

    public Optional<Current> latest(UUID candidateId) {
        return jdbc.sql("SELECT id,version,parsed_json FROM resume WHERE candidate_id=:c ORDER BY version DESC LIMIT 1")
                .param("c", candidateId)
                .query((rs, i) -> new Current(rs.getObject("id", UUID.class), rs.getInt("version"),
                        json.read(rs.getString("parsed_json"), new com.fasterxml.jackson.core.type.TypeReference<ParsedResume>() {})))
                .optional();
    }
}
