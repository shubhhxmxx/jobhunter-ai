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
| Hard eligibility filters (title, location, salary), independent of any LLM | implemented |
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

Job sources (all UNVERIFIED against live APIs in tests; the first deploy returned jobs from the live sources, but treat each as untested):

- `remotive` and `arbeitnow`: public job-board APIs. Remotive asks for sparing use and attribution; at most 3 requests per run.
- `remoteok` and `jobicy`: public remote-job feeds; both require crediting and linking back to the listing, which the UI does.
- `adzuna`: aggregates many Indian job boards. Needs a free App ID and key (developer.adzuna.com) in `JOBHUNTER_ADZUNA_APP_ID` and `JOBHUNTER_ADZUNA_APP_KEY`; without them it is not loaded. Descriptions are short snippets.
- `hackernews`: the monthly "Ask HN: Who is hiring?" thread via the free HN Algolia API (about 3 requests per run). Posts follow a "Company | Role | Location" convention that the parser relies on; posts that don't are skipped. Only posts whose role matches your target titles are kept.
- `fixture`: demo data from `src/main/resources/fixtures/jobs.json`. Every URL is under `example.invalid`, badged "Fixture" in the UI. Disable with `JOBHUNTER_FIXTURES=false`.

Sources deliberately **not** included, because they have no public API that permits this use (scraping them breaks their terms and gets blocked):
Wellfound, LinkedIn, Naukri, Y Combinator's Work at a Startup (login required), Crunchbase (paid API), Product Hunt (needs an OAuth token and lists launches, not jobs), TechCrunch and funding news (articles, not job listings; they would need a separate "startup signals" feature that does not assert anyone is hiring). Adding a key-based adapter for one of these means implementing `JobSource` once you have credentials.

Salary: `salaryFloor` is in LPA (lakh INR per year). It is only applied to postings that state a salary in LPA or lakhs; postings with no salary or another currency are kept and not filtered.

## How jobs are judged

Hard filters (a job failing any is never shown): title match; off-profile roles (UI, test/QA, embedded, mobile, research, sales/support); seniority levels you excluded, plus staff/principal/lead/manager/director/architect automatically when you have under 5 years; location; salary floor; required experience within 1 year of yours; and at least 40% of the skills a posting names being on your resume.

Location: home is India when a target location is an Indian city. Your city and worldwide-remote roles pass; other Indian cities pass and are marked as needing relocation (switch off with "Show other Indian cities"); anything based abroad, including remote roles limited to another country, passes only when the posting explicitly offers visa sponsorship or relocation support, and ranks higher.

Score (0-100): skills 55, title 15, experience 20 (steep penalty when a job asks for more than you have), plus up to 10 for being in your city (5) or offering a visa abroad (10).

## Known limitations

- Visa detection reads explicit wording only ("visa sponsorship", "relocation assistance", a VISA tag on Hacker News posts). A posting that says nothing counts as no visa. Wording it does not recognize is missed.
- The experience figure is the largest "N years ... experience" number in the posting, so an unusual phrasing can be misread. Jobs with no stated figure are not filtered on experience.
- Arbeitnow (a German-language board) is off by default; set `JOBHUNTER_ARBEITNOW=true` to turn it on.

- Repost/ghost warnings are heuristics: listing age over 45 days, and (Hacker News only) the same company and role appearing in the previous two monthly threads. They cannot prove a listing is fake.
- Skill matching is a fixed dictionary (about 60 skills), not semantic. Unlisted skills are invisible to scoring.
- Salary is only detected from text like "20-30 LPA"; most listings do not state one, so the floor affects few jobs.
- Resume years of experience is a heuristic ("N years" in the text); set `yearsExperience` in preferences to override it.
- Scanned (image-only) PDFs are rejected; no OCR.
- Single user per API key; no key rotation or rate limiting on the API itself.
- No scheduled/recurring searches yet (searches are on demand).

## Layout

`domain/` pure logic (no Spring) · `source/` job-source adapters · `service/` persistence and orchestration (Spring `JdbcClient`) ·
`web/` controllers and error handling · `security/` API-key filter and encryption · `llm/` provider abstraction.
