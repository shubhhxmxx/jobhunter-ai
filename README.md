# JobHunter AI

API-first job-search agent: upload a resume, set preferences, run an async search, get ranked jobs with an explainable score.
Java 21, Spring Boot 3.4, PostgreSQL, Flyway, OpenAPI. A small web UI is served at `/` (static HTML, no build step).

## Run it

```bash
cp .env.example .env
# put a real value in JOBHUNTER_ENCRYPTION_KEY:  openssl rand -base64 32
docker compose up --build
```

- UI: http://localhost:8080/
- API docs: http://localhost:8080/swagger-ui.html
- Without Docker: start Postgres (`jobhunter`/`jobhunter`/`jobhunter` on 5432), then
  `export JOBHUNTER_ENCRYPTION_KEY=$(openssl rand -base64 32) && mvn spring-boot:run`
- Tests: `mvn test` (uses in-memory H2, no Docker needed)

Try it with curl:

```bash
KEY=$(curl -s -XPOST localhost:8080/api/v1/setup -H 'content-type: application/json' \
  -d '{"name":"Me","email":"me@example.com"}' | jq -r .apiKey)
curl -XPOST localhost:8080/api/v1/resumes -H "X-API-Key: $KEY" -F file=@resume.pdf
curl -XPUT localhost:8080/api/v1/preferences -H "X-API-Key: $KEY" -H 'content-type: application/json' \
  -d '{"targetTitles":["Backend Engineer"],"locations":["Bengaluru"],"remoteOk":true}'
curl -i -XPOST localhost:8080/api/v1/search-runs -H "X-API-Key: $KEY"     # 202 + Location
curl localhost:8080/api/v1/jobs -H "X-API-Key: $KEY"
```

## What works (milestone 1)

| Area | Status |
|---|---|
| Setup, API-key auth (SHA-256 hashed), per-candidate ownership | implemented |
| PDF resume upload: type/magic-byte/size/page validation, text extraction, versioning, skills/years/sections | implemented |
| Preferences + configurable match threshold (`PUT /preferences`, `PUT /settings/matching`) | implemented |
| BYOK: AES-256-GCM at rest, key never returned (only last 4), masked in `toString` | implemented |
| Hard eligibility filters (title, location, salary, experience range), independent of any LLM | implemented |
| Explainable 0-100 score: matched skills, missing requirements, evidence, uncertainty | implemented |
| Score caching keyed on resume version + preferences hash + scoring version | implemented |
| Async search runs: `202` + `Location`, per-source status | implemented |
| Job dedup across sources (normalized company/title/place) | implemented |
| Web UI | implemented |
| Contact discovery, outreach drafts | **not implemented**: endpoints return `501`; tables exist in the migration only |
| LLM-assisted scoring or drafting | **not implemented**: `AnthropicProvider` exists but nothing calls it yet |

## Verification status (read this)

The environment this was generated in could not reach Maven Central, so **the Spring Boot application has not been compiled by Maven and `mvn test` has not been run.**
What was checked:

- The dependency-free domain package (`com.jobhunter.domain`: filters, scorer, skill extraction, resume parsing, dedup keys) was compiled with `javac` and `DomainTest` was run: 9/9 passing.
- All other Java files were only syntax-checked. Expect to fix small compile or wiring errors on first `mvn test`.
- `ApiFlowTest` (full HTTP flow on H2) is written but has never run. Postgres has not been exercised at all; the migration avoids Postgres-only features so it should also run on H2.

Job sources:

- `fixture`: demo data from `src/main/resources/fixtures/jobs.json`. Every URL is under `example.invalid` and the UI badges them "Fixture". Disable with `JOBHUNTER_FIXTURES=false`.
- `remotive` and `arbeitnow`: written against the public API shapes but **not yet run against the live APIs**. Treat them as untested. Remotive asks for sparing use and attribution; the adapter makes at most 3 requests per run. Each adapter sends one request per search and reports HTTP 429 as a failed source.

## Known limitations

- Skill matching is a fixed dictionary (about 60 skills), not semantic. Unlisted skills are invisible to scoring.
- Salary filter compares raw numbers; no currency handling. Adapters do not populate salary yet, so the filter only affects sources that provide it.
- Resume years of experience is a heuristic ("N years" in the text); set `yearsExperience` in preferences to override it.
- Scanned (image-only) PDFs are rejected; no OCR.
- Single user per API key; no key rotation or rate limiting on the API itself.
- No scheduled/recurring searches yet (searches are on demand).

## Layout

`domain/` pure logic (no Spring) · `source/` job-source adapters · `service/` persistence and orchestration (Spring `JdbcClient`) ·
`web/` controllers and error handling · `security/` API-key filter and encryption · `llm/` provider abstraction.
