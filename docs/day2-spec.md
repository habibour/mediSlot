# Day 2 Spec — Business Logic, Patterns, AOP, Search

**Covers:** FR-6, FR-7, FR-8, FR-9, FR-10, FR-11, FR-12, FR-13 · NFR-3, NFR-5
**Companion:** `day2-plan.md` · **Depends on:** Day 1 complete

## Scope
Appointment booking and lifecycle, prescriptions, event-driven notifications, AOP audit trail, Elasticsearch search, MySQL profile, field encryption.

## Data model additions
- Flyway `V3__appointments_prescriptions_audit.sql` (postgresql **and** mysql folders):
  - `appointments(... status, reason, version)`; uniqueness of an *active* slot is a **partial** unique index `(doctor_id, start_time) WHERE status = 'BOOKED'` (MySQL: generated column `active_start` + unique index), so cancelled slots stay rebookable; indexes on `(doctor_id, start_time)` and `(patient_id, start_time)`
  - `prescriptions`, `audit_logs` per PRD §6
- `patients.national_id_enc` already exists from V1 (nullable, stores AES-GCM ciphertext, Base64).
- Mirror `V1`, `V2` into `db/migration/mysql/` (identity → `AUTO_INCREMENT`, `timestamptz` → `DATETIME(6)`).

## Endpoints
| Method & path | Role | Behaviour |
|---|---|---|
| `POST /api/appointments` | PATIENT | Body `{doctorId, startTime, reason}`; duration = doctor's `slot_minutes`; 201 or 409/422 |
| `GET /api/appointments/mine` | PATIENT, DOCTOR | Own appointments, paginated, optional `status` |
| `PATCH /api/appointments/{id}/cancel` | PATIENT (own), DOCTOR (own), ADMIN | BOOKED → CANCELLED |
| `PATCH /api/appointments/{id}/complete` | DOCTOR (own) | BOOKED → COMPLETED |
| `POST /api/appointments/{id}/prescriptions` | DOCTOR (own appt) | Adds prescription |
| `GET /api/patients/{id}/prescriptions` | PATIENT (self), DOCTOR (has appt with patient), ADMIN | List |
| `GET /api/search/doctors?q=&specialty=` | authenticated | Fuzzy search |
| `GET /api/search/patients?q=` | DOCTOR, ADMIN | Fuzzy search by name; hits are hydrated through `PatientService`, so a DOCTOR only sees patients they have an appointment with |
| `GET /api/admin/audit-logs` | ADMIN | Paginated, filter by `actorUserId`, `resourceType`, date range |
| `POST /api/admin/search/reindex` | ADMIN | Rebuild ES indices from DB |

Also tighten Day 1: `GET /api/patients/{id}` for DOCTOR only if an appointment links them (ADMIN unrestricted).

## Business rules (FR-6)
`SlotPolicy` strategies, all must pass:
1. `WorkingHoursPolicy` — start/end inside doctor's `working_start`–`working_end`, aligned to `slot_minutes`.
2. `MinLeadTimePolicy` — start at least 30 min in the future.
3. `NoOverlapPolicy` — no BOOKED appointment for that doctor overlapping `[start,end)`; patient also cannot have two overlapping BOOKED appointments.

Rule violations (hours, alignment, lead time) → **422** with the failing policy in the ProblemDetail `policy` field. Overlap (doctor or patient) → **409**. Concurrency: booking takes `PESSIMISTIC_WRITE` row locks on the patient, then the doctor (fixed order, no deadlock), then runs the policies; the partial unique index and `@Version` are backstops. Exactly one concurrent booking wins; the rest get 409.

Time: instants are stored in UTC; the request `startTime` is ISO-8601 with offset; working hours and slot alignment are evaluated in `app.clinic.zone` (default `Asia/Dhaka`). The JVM is pinned to UTC (`MedislotApplication.main`, surefire/failsafe `argLine`) so JDBC drivers never shift `LocalTime`/`LocalDate`.

## Patterns (explicit deliverables)
- **Strategy:** `SlotPolicy` interface + 3 impls, injected as `List<SlotPolicy>`.
- **Factory:** `NotificationSenderFactory.forChannel(Channel)` → `EmailSender`/`SmsSender` (log a masked message).
- **Builder:** `Notification` built with a builder; response DTOs via records/builders.
- **Observer:** `AppointmentBookedEvent`, `AppointmentCancelledEvent` published from the service; `@TransactionalEventListener(AFTER_COMMIT)` listeners for notifications and search indexing.

## AOP (FR-10)
- `@Audited(action="READ_PATIENT", resource="PATIENT")` annotation; `AuditAspect` (`@Around`) writes `audit_logs` row (actor from `SecurityContext`, resource id from first `Long`/`id` argument or result) on success; failures to write the audit row must not break the request but must be logged.
- Annotated: patient read endpoints' service methods, prescription read/write, appointment complete.
- `TimingAspect` logs WARN for any `@Service` method > 200 ms.
- Document the self-invocation limitation; audited methods are called only from controllers.

## Elasticsearch (FR-11)
- `DoctorDocument(id, fullName, specialty, bio)`, `PatientDocument(id, fullName)` — **no** national ID, DOB, phone in the index (data minimisation).
- `multi_match` with `fuzziness=AUTO` over name (boost 3), specialty (boost 2), bio.
- Index kept in sync by listeners on create/update events; reindex endpoint streams from DB in batches of 500.
- Search returns relevance-ordered IDs; entity details are fetched through the normal services so authorisation still applies (highlights were dropped: not needed by any acceptance criterion). The whole search package is switchable with `app.search.enabled` (default true) so tests that do not need Elasticsearch run without a node.

## Encryption (FR-13)
- `AesGcmConverter implements AttributeConverter<String,String>` on `Patient.nationalId`; 256-bit key from `FIELD_ENCRYPTION_KEY` (Base64) env var; random 12-byte IV prepended to ciphertext.
- API accepts national ID on register/update; responses return masked value (`******1234`).

## MySQL profile (FR-12)
- `application-mysql.yml` (driver, dialect auto, Flyway vendor resolves to `mysql`).
- Same app, same tests, different DB: documented command `SPRING_PROFILES_ACTIVE=dev,mysql`.

## Acceptance criteria
| ID | Criterion | FR/NFR |
|---|---|---|
| D2-AC1 | Valid booking returns 201 with computed end time and status BOOKED | FR-6 |
| D2-AC2 | Outside working hours, misaligned slot, or < 30 min lead → 422 naming the policy | FR-6 |
| D2-AC3 | Overlapping booking for same doctor, or same patient (even across different doctors) → 409 | FR-6 |
| D2-AC4 | 20 concurrent requests for one slot — from 20 patients, from 1 patient, and from 1 patient across 20 doctors — each yield exactly 1× 201 and 19× 409; a cancelled slot can be rebooked (integration tests) | FR-6 |
| D2-AC5 | Cancel/complete follow state machine; illegal transitions (e.g. cancel COMPLETED) → 409; ownership enforced → 403 | FR-7 |
| D2-AC6 | `GET /mine` returns only caller's appointments, paginated, filter by status | FR-7 |
| D2-AC7 | Doctor can add prescriptions only to own appointments; patient reads own only | FR-8 |
| D2-AC8 | Booking/cancel publish events; listener fires after commit (not on rollback); sender chosen by factory | FR-9 |
| D2-AC9 | Every audited call creates one `audit_logs` row with correct actor/resource; admin can query with filters | FR-10 |
| D2-AC10 | Audit write failure does not fail the business call (unit test with failing repo) | FR-10 |
| D2-AC11 | Creating a doctor makes them searchable within seconds (Elasticsearch refresh interval ≈ 1 s); a one-edit typo (`cardiolgy` for `Cardiology`) still matches | FR-11 |
| D2-AC12 | Index documents contain no PII beyond name; reindex rebuilds from empty index | FR-11 |
| D2-AC13 | The whole test suite passes on MySQL (`./mvnw verify -Dtest.db=mysql`); app runs with `SPRING_PROFILES_ACTIVE=dev,mysql` | FR-12 |
| D2-AC14 | National ID is ciphertext in DB, masked in API; wrong key fails loudly on startup/test | FR-13 |
| D2-AC15 | Search and booking p95 < 300 ms on seeded data (simple local measurement script) | NFR-5 |
| D2-AC16 | Logs contain no emails/names/national IDs (grep check after a full run) | NFR-3 |

## Definition of done (Day 2)
- [ ] All D2-AC verified (D2-AC4, AC10 via automated tests; others via Postman run)
- [ ] `./mvnw verify` green on Postgres
- [ ] Postman collection extended with Day 2 folders
- [ ] Pushed with conventional commits (`feat: booking`, `feat: audit aspect`, `feat: search`, …)

## Implementation notes (what changed versus the first draft)
- **Partial unique index** instead of `UNIQUE(doctor_id, start_time)`: the plain constraint made a cancelled slot permanently unbookable.
- **Row locks** (patient → doctor) are what protect the patient-side overlap across different doctors; the unique index cannot. A mutation test (patient lock removed) proves the cross-doctor race test fails without it.
- **UTC pinning**: a global `hibernate.jdbc.time_zone=UTC` was tried first for MySQL and shifted `LocalTime` by the JVM offset (symmetrically, so API round-trips still passed). `StoredValuesIT` now asserts on the physically stored values.
- **Role gates at URL level** for write endpoints (`SecurityConfig`) so a wrong-role caller gets 403 before request-body validation can return 400. `@PreAuthorize` remains as a second layer.
- **Failsafe** is configured; integration tests are `*IT` and run in `verify`.
- Doctors reading patients (`GET /api/patients/{id}`, prescriptions, search) are limited to patients they have an appointment with.
