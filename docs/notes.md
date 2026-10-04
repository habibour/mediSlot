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

## Interview cheat sheet (be able to explain each of these from the code)

**Request path.** `JwtAuthenticationFilter` (a `OncePerRequestFilter` placed before `UsernamePasswordAuthenticationFilter`) parses the bearer token and puts an `AuthenticatedUser(userId, role)` in the `SecurityContext`. URL rules in `SecurityConfig` run first (so a wrong-role caller gets 403 before body validation can return 400); `@PreAuthorize` is the second layer; ownership checks live in the services because only they know who owns a row. Stateless: no session, CSRF disabled for a token API. Entry point and access-denied handler write RFC 7807 JSON because `@RestControllerAdvice` does not see filter-level errors.

**Why constructor injection.** Immutable dependencies, no reflection to test, circular dependencies fail at startup, and the class works in a plain unit test (`new AuthService(...)` with mocks).

**Spring AOP.** Proxy-based: Spring wraps beans in JDK/CGLIB proxies, so advice runs only for calls that go *through* the proxy. A method calling another method on `this` bypasses it (self-invocation); that is why `@Audited` sits on service methods called from controllers. `@Transactional` has the same limitation. The audit write uses `REQUIRES_NEW` so it commits independently and a failure there cannot mark the business transaction rollback-only.

**Locking.** Optimistic (`@Version`) detects a conflict at write time and suits low contention; pessimistic (`SELECT … FOR UPDATE`) serialises at read time and suits "check then insert" invariants like booking. Lock order (patient → doctor) is fixed so two transactions can never wait on each other. The partial unique index is the database-level guarantee if application logic ever has a hole.

**JPA.** N+1: loading a list then touching a lazy association per row issues one query per row; fix with fetch joins, entity graphs or (as here) not mapping associations at all and querying by id. `open-in-view=false` keeps lazy loading out of controllers. `ddl-auto=validate` with Flyway makes migrations the only source of schema truth.

**After-commit events.** `@TransactionalEventListener(AFTER_COMMIT)` runs only if the transaction committed, so a rolled-back booking sends no email and indexes nothing. Trade-off: if the listener fails the data is already committed, so side effects must be best-effort and repairable (the reindex endpoint).

**Elasticsearch basics.** Text fields are analysed into an inverted index (term → documents). `fuzziness=AUTO` allows 1 edit for 3–5 character terms and 2 for longer ones (Levenshtein), which is why `cardiolgy` finds `Cardiology` but a three-edit typo does not. The index is a derived view: the database stays the source of truth.

**PostgreSQL vs MySQL differences met here.** No partial indexes in MySQL (generated column trick); `GENERATED … AS IDENTITY` vs `AUTO_INCREMENT`; `TIMESTAMPTZ` vs `DATETIME(6)` (store UTC, pin the JVM zone); case-insensitive default collation in MySQL makes `lower()` indexes unnecessary.

**Security reasoning.** BCrypt is deliberately slow and salted; the login path compares against a dummy hash for unknown emails so response time does not reveal which emails exist. AES-GCM gives confidentiality and integrity (tampering fails on read); a fresh random IV per value means equal plaintexts encrypt differently. Data minimisation: the search index holds names only.

**Things I would do next (say so if asked about limitations).** Password change and forced rotation of the seeded admin, refresh tokens, rate limiting on login, a real notification transport behind the existing Factory, Oracle support, and measuring under concurrent load rather than sequential requests.
