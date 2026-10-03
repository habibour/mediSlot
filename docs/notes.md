# Engineering notes (Day 1–2)

## Measured (local laptop, seeded: 1,000 doctors / 10,000 patients, Postgres 16 + Elasticsearch 8.18 in Docker)
`scripts/latency.sh` (curl, sequential, includes TLS-less localhost HTTP):

| Operation | n | p50 | p95 |
|---|---|---|---|
| Typo-tolerant doctor search | 200 | 12 ms | 29 ms (cold, first run after start) / 10 ms (warm) |
| Book appointment (all 201) | 64 | 11 ms | 61 ms cold / 15 ms warm |

Target was p95 < 300 ms (NFR-5). Single-user sequential numbers, not a load test; concurrency correctness is covered by the race tests instead.

## Gotchas hit (good interview stories)
1. **Partial unique index.** `UNIQUE(doctor_id, start_time)` blocks rebooking a cancelled slot. Postgres: partial index. MySQL has none: generated column that is `NULL` unless `BOOKED`, unique index over it (NULLs never collide).
2. **A unique index is not a concurrency strategy.** It stops identical doctor+start only. A patient booking two doctors at the same time needs a serialisation point: row locks on patient then doctor, in a fixed order to avoid deadlock. Verified by removing the patient lock and watching the cross-doctor race test fail.
3. **Symmetric bugs hide from round-trip tests.** `hibernate.jdbc.time_zone=UTC` shifted `LocalTime` on write and back on read, so every API test passed while the DB held `03:00` for a `09:00` shift. Fix: pin the JVM to UTC, assert on stored values (`StoredValuesIT`), keep clinic wall-clock logic explicit.
4. **Validation runs before method security.** Without a URL-level gate a wrong-role caller with a bad body got 400 instead of 403.
5. **`*IT` classes are not run by surefire.** Failsafe must be configured or `verify` silently skips them.
6. **AOP only sees proxy calls.** `@Audited` works because controllers call the service through the Spring proxy; a same-class call would bypass it. Audit rows are written in `REQUIRES_NEW` and failures are logged, never propagated.
7. **AFTER_COMMIT listeners** (notifications, search indexing) keep side effects from firing on rolled-back transactions; indexing is best-effort and healed by `POST /api/admin/search/reindex`.

## Running
- Infra: `docker compose -f docker-compose.dev.yml up -d` (Postgres 5432, Elasticsearch 9200); MySQL: `docker compose -f docker-compose.mysql.yml up -d` (3307).
- App: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` (port 8081); add `,seed` for perf data, `,mysql` to target MySQL.
- Tests: `./mvnw verify` (Postgres) · `./mvnw verify -Dtest.db=mysql`.
