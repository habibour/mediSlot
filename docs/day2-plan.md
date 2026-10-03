# Day 2 Plan — Business Logic, Patterns, AOP, Search

**Implements:** `day2-spec.md` · **Budget:** ~10–11 h · Tasks list the acceptance criteria they satisfy.

## Block 1 — Schema & appointment core (2 h) → D2-AC1, D2-AC2, D2-AC3
- [ ] 1.1 `postgresql/V3__appointments_prescriptions_audit.sql` with a partial unique index `(doctor_id, start_time) WHERE status = 'BOOKED'` and indexes.
- [ ] 1.2 Entities `Appointment` (with `@Version`), `Prescription`, `AuditLog`; enum `AppointmentStatus`; repositories. Overlap query:
  `existsByDoctorIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(...)`; same for patient.
- [ ] 1.3 `appointment/policy/SlotPolicy` + `WorkingHoursPolicy`, `MinLeadTimePolicy`, `NoOverlapPolicy`; `PolicyViolationException` carrying policy name → 422 in `GlobalExceptionHandler`.
- [ ] 1.4 `AppointmentService.book()` — `@Transactional`; lock patient then doctor (`PESSIMISTIC_WRITE`), build candidate, run `List<SlotPolicy>`, save, catch `DataIntegrityViolationException`/`OptimisticLockingFailureException` → `SlotTakenException` (409).
- [ ] 1.5 `AppointmentController` `POST /api/appointments`.
- **Checkpoint:** Postman happy path + three rule violations.

## Block 2 — Lifecycle & concurrency test (1.5 h) → D2-AC4, D2-AC5, D2-AC6
- [ ] 2.1 `cancel`, `complete` with explicit state-transition method on the entity (`appointment.cancel()` throws on illegal state). Ownership checks in the service (not just annotations).
- [ ] 2.2 `GET /mine` with `Pageable` + optional status; patient vs doctor resolved from `CurrentUser`.
- [ ] 2.3 Concurrency integration test: Testcontainers Postgres, 20 threads via `ExecutorService` + `CountDownLatch` booking the same slot; assert 1 success, 19 conflicts.
- **Checkpoint:** test green; try an illegal transition manually.

## Block 3 — Prescriptions (45 min) → D2-AC7
- [ ] 3.1 `PrescriptionService`/`Controller`: add (doctor owns appointment), list by patient (self / doctor with linking appointment / admin).
- [ ] 3.2 Tighten `PatientService.getById` for DOCTOR to linked patients only.

## Block 4 — Events & notifications (1 h) → D2-AC8
- [ ] 4.1 Event records `AppointmentBookedEvent`, `AppointmentCancelledEvent`; publish via `ApplicationEventPublisher` inside the service.
- [ ] 4.2 `notification/Notification` (builder), `NotificationSender` interface, `EmailSender`, `SmsSender` (log with masked recipient), `NotificationSenderFactory`.
- [ ] 4.3 `NotificationListener` with `@TransactionalEventListener(phase = AFTER_COMMIT)`; `@Async` optional.
- [ ] 4.4 Unit test: verifies listener not invoked on rollback (use `@Transactional` test + `TestTransaction` or assert via publisher spy).

## Block 5 — AOP audit (1.5 h) → D2-AC9, D2-AC10, D2-AC16
- [ ] 5.1 `audit/Audited` annotation, `AuditAspect` (`@Around`), `AuditLogService.record(...)` with `REQUIRES_NEW` so an audit failure cannot poison the business transaction; wrap in try/catch + error log.
- [ ] 5.2 Annotate service methods listed in spec; ensure they are invoked through the proxy (controller → service).
- [ ] 5.3 `TimingAspect` for `@Service` classes (`@Around("within(@org.springframework.stereotype.Service *)")`).
- [ ] 5.4 `GET /api/admin/audit-logs` with filters (Spring Data `Specification` or derived query).
- [ ] 5.5 Tests: aspect writes a row (slice/integration); failing `AuditLogService` doesn't break call (Mockito).
- [ ] 5.6 Log hygiene pass: grep run logs for email/name/ID; remove offenders.

## Block 6 — Elasticsearch (2 h) → D2-AC11, D2-AC12
- [ ] 6.1 Add `spring-boot-starter-data-elasticsearch`; extend `docker-compose.dev.yml` with ES 8 single-node (`discovery.type=single-node`, `xpack.security.enabled=false`, 512 MB heap).
- [ ] 6.2 `DoctorDocument`, `PatientDocument`, ES repositories.
- [ ] 6.3 `SearchIndexListener` reacting to create/update events (publish events from doctor/patient services too).
- [ ] 6.4 `SearchService`: `multi_match` fuzzy query via `NativeQuery`/`ElasticsearchOperations`; `SearchController` with the role rules.
- [ ] 6.5 `POST /api/admin/search/reindex` — page through DB in batches of 500, `saveAll`.
- [ ] 6.6 Verify typo search and that national ID/phone never appear in `GET localhost:9200/doctors/_search`.

## Block 7 — Encryption (45 min) → D2-AC14
- [ ] 7.1 `AesGcmConverter` + `@Convert` on `Patient.nationalId`; key via `@ConfigurationProperties` (`FIELD_ENCRYPTION_KEY`).
- [ ] 7.2 DTO masking helper; accept national ID in register/update.
- [ ] 7.3 Unit test: round trip, tamper detection (modified ciphertext throws), distinct IVs; check DB row is ciphertext in DBeaver.

## Block 8 — MySQL profile (1 h) → D2-AC13
- [ ] 8.1 Port V1–V3 to `db/migration/mysql/` (AUTO_INCREMENT, `DATETIME(6)`, generated column for the active-slot unique index).
- [ ] 8.2 `application-mysql.yml`; add `mysql-connector-j` (runtime); add MySQL service to a `docker-compose.mysql.yml`.
- [ ] 8.3 Run the whole suite on MySQL with `-Dtest.db=mysql`; note differences in `docs/notes.md`.
- **Fallback:** if this overruns, finish smoke-test on Day 3 morning; don't skip, it's a job-post line.

## Block 9 — Perf sanity & wrap-up (45 min) → D2-AC15, DoD
- [ ] 9.1 Seed script (`scripts/seed.sh` or `@Profile("seed")` runner): ~1k doctors, 10k patients; reindex.
- [ ] 9.2 Quick latency check (curl loop or `hey`/`ab`) for booking and search; record numbers in `docs/notes.md`.
- [ ] 9.3 Update Postman collection; `./mvnw verify`; commit and push.

## Traceability
| Spec criterion | Plan tasks |
|---|---|
| D2-AC1 | 1.4, 1.5 |
| D2-AC2 | 1.3 |
| D2-AC3 | 1.2, 1.3 |
| D2-AC4 | 1.1 (unique constraint), 1.4, 2.3 |
| D2-AC5 | 2.1 |
| D2-AC6 | 2.2 |
| D2-AC7 | 3.1, 3.2 |
| D2-AC8 | 4.1–4.4 |
| D2-AC9 | 5.1, 5.2, 5.4, 5.5 |
| D2-AC10 | 5.1, 5.5 |
| D2-AC11 | 6.3, 6.4, 6.6 |
| D2-AC12 | 6.2, 6.5, 6.6 |
| D2-AC13 | 8.1–8.3 |
| D2-AC14 | 7.1–7.3 |
| D2-AC15 | 9.1, 9.2 |
| D2-AC16 | 5.6 |

## If you fall behind
Cut order: `TimingAspect` (5.3) → `@Async` on listener → MySQL fuzzy edge cases (document known gaps) → latency script (do a single manual check). Never cut: unique constraint + concurrency test, audit aspect, ES search.

## Status
All blocks done (see `docs/notes.md` for measurements and gotchas). Verified: `./mvnw verify` → 83 tests green on PostgreSQL 16 and on MySQL 8.4 (`-Dtest.db=mysql`); search tests run against Elasticsearch 8.18 via Testcontainers.
