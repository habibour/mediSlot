# Day 3 Plan — Quality, Delivery, Presentation

**Implements:** `day3-spec.md` · **Budget:** ~10 h · Tasks list the acceptance criteria they satisfy.

## Block 1 — Close Day 2 leftovers (0.5–1 h)
- [ ] 1.1 Finish anything cut on Day 2 (MySQL smoke test first). Don't carry hidden bugs into the test phase.

## Block 2 — Unit & slice tests (2.5 h) → D3-AC1, D3-AC3
- [ ] 2.1 Unit tests (Mockito/AssertJ): `AppointmentServiceTest` (happy path, each policy failure, conflict translation, state transitions), one test class per `SlotPolicy` with boundary cases (exact start/end, adjacent slots allowed), `NotificationSenderFactoryTest`, `AesGcmConverterTest`, `AuditAspectTest`.
- [ ] 2.2 `@WebMvcTest` per controller with `@WithMockUser`/custom JWT principal: role matrix table-driven (`@ParameterizedTest` with endpoint × role × expected status). Validate ProblemDetail body shape once.
- [ ] 2.3 `@DataJpaTest` + Testcontainers Postgres (`@ServiceConnection`): overlap queries, specialty filter, unique constraint, audit filters.
- **Checkpoint:** `./mvnw test` green; no `@Disabled`.

## Block 3 — Integration test & coverage gate (1.5 h) → D3-AC2, D3-AC4
- [ ] 3.1 Base class `AbstractIntegrationTest` with shared static Postgres + Elasticsearch containers (singleton pattern for speed).
- [ ] 3.2 `FullFlowIT` using `TestRestTemplate`/`MockMvc`: register patient → login → admin creates doctor → search doctor (await indexing with Awaitility) → book → doctor completes → prescription → patient reads → admin reads audit log and asserts rows.
- [ ] 3.3 Keep the Day 2 concurrency test in the suite.
- [ ] 3.4 JaCoCo plugin in `pom.xml`: `report` + `check` rule (LINE ≥ 0.70, includes `**/service/**`); run `verify` in failsafe/surefire as appropriate.
- **Checkpoint:** `./mvnw verify` green, coverage report in `target/site/jacoco`.

## Block 4 — Docker (1.5 h) → D3-AC5, D3-AC6, D3-AC13
- [ ] 4.1 Multi-stage `Dockerfile`, `.dockerignore`, non-root user, `HEALTHCHECK`.
- [ ] 4.2 `docker-compose.yml` (app, postgres, elasticsearch) with healthchecks, env from `.env`; commit `.env.example`; make sure `.gitignore` covers `.env`, `target/`, `.idea/` (keep run configs out).
- [ ] 4.3 Clean-slate test: `docker compose down -v && docker compose up --build`; run admin login + a booking.
- [ ] 4.4 Secret scan: `git grep -nEi "secret|password|apikey"` and inspect results; confirm none real.

## Block 5 — CI (45 min) → D3-AC7
- [ ] 5.1 `.github/workflows/ci.yml` as per spec; push; fix runner-specific issues (Docker available, memory for ES container — set heap small in test containers).
- [ ] 5.2 Add badge to README once green.

## Block 6 — Postman, Swagger, ER diagram (1.25 h) → D3-AC8, D3-AC9, D3-AC10
- [ ] 6.1 Finalise collection: pre-request/test scripts, environment file, "Full demo flow" folder; run via Postman collection runner (or `newman run`) against the compose stack → 100% pass.
- [ ] 6.2 Verify Swagger UI Authorize flow; add short descriptions/summary annotations to controllers.
- [ ] 6.3 Connect DBeaver to Postgres → generate ER diagram → save `docs/er-diagram.png`.

## Block 7 — AWS deployment (1.5 h) → D3-AC11
- [ ] 7.1 Launch EC2 (Amazon Linux/Ubuntu, t3.small+ with 20 GB), install Docker + compose plugin, security group: 80/443 world, 22 from your IP only.
- [ ] 7.2 Copy repo (git clone), create `.env` with strong secrets (`JWT_SECRET`, `FIELD_ENCRYPTION_KEY`, DB password), `docker compose up -d --build`.
- [ ] 7.3 Add Caddy/nginx service for 80/443 → `app:8080`; do not publish 5432/9200 to host.
- [ ] 7.4 Run Postman collection with deployed `baseUrl`; confirm ports 5432/9200 unreachable externally (`nmap`/`nc` from your laptop).
- [ ] 7.5 Set a billing alarm / remember to stop the instance after the review window.

## Block 8 — README, notes, polish (1.5 h) → D3-AC12, D3-AC13
- [ ] 8.1 Write README per spec sections (architecture diagram can be the ASCII from PRD §8 or a draw.io export), badge, demo URL, test credentials (demo-only).
- [ ] 8.2 "How I used AI tools" section: tools used (e.g. Claude Code/Cursor/Codex) for scaffolding, test generation, review; what you verified, changed or rejected — be honest and specific.
- [ ] 8.3 Record 2-minute demo (screen capture → GIF/video link): login, book, conflict, audit log, search.
- [ ] 8.4 Finish `docs/notes.md` interview cheat sheet: patterns (PRD §9), JWT filter chain, AOP proxies & self-invocation, N+1/lazy loading, optimistic vs pessimistic locking, why Flyway, Postgres vs MySQL differences hit, ES inverted index/fuzziness basics.
- [ ] 8.5 Final pass: `git status` clean, tidy commit history (squash noise only if not yet shared), tag `v1.0.0`.

## Stretch (only if all above are done)
- [ ] S1 Oracle XE profile (`gvenzl/oracle-free`), `db/migration/oracle/`, `application-oracle.yml`, smoke test.
- [ ] S2 Rate limiting on `/api/auth/login` (Bucket4j) → mention in README security notes.

## Traceability
| Spec criterion | Plan tasks |
|---|---|
| D3-AC1 | 2.1–2.3, 3.4 |
| D3-AC2 | 3.4 |
| D3-AC3 | 2.2 |
| D3-AC4 | 3.1, 3.2, 3.3 |
| D3-AC5 | 4.1–4.3 |
| D3-AC6 | 4.1, 4.2, 4.4 |
| D3-AC7 | 5.1, 5.2 |
| D3-AC8 | 6.1 |
| D3-AC9 | 6.2 |
| D3-AC10 | 6.3 |
| D3-AC11 | 7.1–7.5 |
| D3-AC12 | 8.1–8.4 |
| D3-AC13 | 4.2, 4.4, 8.5 |

## If you fall behind
Cut order: Stretch items → Caddy/HTTPS (deploy plain HTTP) → `@DataJpaTest` extras → demo video (keep GIF). Never cut: coverage gate, CI green, Docker compose, README + AI-tools section.

## Status
Blocks 1–8 done except the live AWS deployment (runbook only) and an observed green run on GitHub Actions (pending the push). See the implementation notes in `day3-spec.md`.
