# Day 3 Spec — Quality, Delivery, Presentation

**Covers:** FR-14, FR-15, FR-16, FR-17, FR-18, FR-19 · NFR-8
**Companion:** `day3-plan.md` · **Depends on:** Day 1–2 complete

## Scope
Make the project trustworthy and presentable: automated tests with a coverage gate, containers, CI, API tooling, cloud deployment and a README that sells it.

## Testing strategy (FR-14)
| Layer | Tool | Targets |
|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | `AppointmentService`, each `SlotPolicy`, `AuthService`, `JwtService`, `AuditAspect`, `AesGcmConverter`, `NotificationSenderFactory` |
| Web slice | `@WebMvcTest` + `spring-security-test` | Role matrix for every controller (401/403/200), validation errors, ProblemDetail shape |
| Data slice | `@DataJpaTest` + Testcontainers Postgres | Repository queries (overlap, specialty filter, audit filters), unique constraint |
| Integration | `@SpringBootTest` + Testcontainers (Postgres + Elasticsearch) | End-to-end flow: register → login → admin creates doctor → search → book → complete → prescription → audit row; concurrency test from Day 2 |
| Coverage | JaCoCo | Service-layer line coverage ≥ 70% enforced in `verify` |

## Containerisation (FR-15)
- Multi-stage `Dockerfile` (Maven build → `eclipse-temurin:21-jre` runtime), non-root user, `HEALTHCHECK` on `/actuator/health`.
- `docker-compose.yml`: `app`, `postgres`, `elasticsearch` with healthchecks and `depends_on: condition: service_healthy`; config via `.env` (`.env.example` committed, `.env` ignored).
- Optional: `docker-compose.oracle.yml` + `application-oracle.yml` + `db/migration/oracle/` (stretch; only if everything else is done).

## CI (FR-16)
GitHub Actions `.github/workflows/ci.yml`: checkout → setup-java 21 (Maven cache) → `./mvnw verify` (Testcontainers works on hosted runners) → upload JaCoCo report artifact → status badge in README. Triggers: push to `main`, all PRs.

## API tooling (FR-17)
- Postman collection `postman/MediSlot.postman_collection.json` + `MediSlot.local.postman_environment.json`: folders per area, each request with test scripts (status, schema fields, saves `token`/ids to variables); a "Full demo flow" folder runnable with the collection runner; passes against local and deployed URLs.
- springdoc OpenAPI at `/swagger-ui.html`, bearer auth configured.
- ER diagram exported from DBeaver (`docs/er-diagram.png`).

## Deployment (FR-18)
- AWS EC2 (t3.small or larger because of Elasticsearch), Docker + compose, security group exposing only 80/443 (+22 restricted to own IP); Elasticsearch and Postgres not publicly reachable.
- Reverse proxy (Caddy or nginx) → app:8080; HTTPS if a domain is available, otherwise HTTP with clear note.
- Secrets provided via `.env` on the instance, never committed.

## README (FR-19)
Sections: one-paragraph pitch, feature list, architecture diagram, tech stack, **job-post mapping table**, quick start (`docker compose up`), running tests, Postman/Swagger usage, design-pattern map, security notes, DB portability (Postgres/MySQL), demo URL + test credentials, **"How I used AI tools"** (which tools, what they did, what you verified/rewrote by hand), known limitations.

## Acceptance criteria
| ID | Criterion | FR/NFR |
|---|---|---|
| D3-AC1 | `./mvnw verify` passes locally with zero skipped tests | FR-14 |
| D3-AC2 | JaCoCo gate enforces ≥ 70% line coverage on `..service..` packages; build fails below | FR-14, NFR-8 |
| D3-AC3 | Role-matrix web tests cover every endpoint in PRD §7 (401/403/success) | FR-14 |
| D3-AC4 | Full-flow integration test passes against real Postgres + Elasticsearch containers | FR-14 |
| D3-AC5 | `docker compose up --build` on a clean machine yields healthy app, Postgres, ES; `/actuator/health` UP | FR-15 |
| D3-AC6 | Image runs as non-root; no secrets baked into image or committed | FR-15 |
| D3-AC7 | GitHub Actions run is green on `main`; badge visible in README | FR-16 |
| D3-AC8 | Postman collection runner passes 100% against local stack | FR-17 |
| D3-AC9 | Swagger UI loads, "Authorize" works with a login token | FR-17 |
| D3-AC10 | `docs/er-diagram.png` generated from the live schema via DBeaver | FR-17 |
| D3-AC11 | Public URL serves `/actuator/health`; Postman collection passes against it; ES/DB ports closed to the internet | FR-18 |
| D3-AC12 | README satisfies section list above; a reader can run the project in < 5 minutes | FR-19 |
| D3-AC13 | Repo is clean: no `.env`, keys or build output committed; history is readable | FR-15, FR-19 |

## Definition of done (project)
- [ ] All D1/D2/D3 criteria met
- [ ] Repo public on GitHub with README, docs/, Postman collection
- [ ] Demo URL live and listed in README (and in the job application/CV)
- [ ] 2-minute screen-recording/GIF of the demo flow linked in README
- [ ] Interview cheat sheet (`docs/notes.md`) has answers for the PRD §9 patterns and the Day 1/2 talking points
