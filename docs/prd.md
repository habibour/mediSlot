# MediSlot — Product Requirements Document

**Owner:** Habib · **Target:** Square Health Ltd, Jr. Java Developer application · **Build window:** 3 days
**Stack:** Java 21, Spring Boot 3.x, PostgreSQL (primary) / MySQL (profile) / Oracle XE (stretch), Elasticsearch 8, Docker, GitHub Actions, AWS

## 1. Problem
Clinics juggle appointment booking, patient records and prescriptions across paper and spreadsheets. Data is hard to search, access is not audited, and double-booking is common. MediSlot is a secure REST backend that handles booking, records and search with role-based access and a full audit trail.

*Real purpose:* a portfolio project that demonstrates, in one coherent codebase, every skill in the job post.

## 2. Goals / Non-goals
**Goals**
- G1. Secure, role-based API for patients, doctors and admins.
- G2. Conflict-free appointment booking (no double booking, even under concurrency).
- G3. Every access to patient data is audited.
- G4. Fast fuzzy search over doctors and patients (Elasticsearch).
- G5. One-command run (`docker compose up`), green CI, Postman collection, deployed demo.

**Non-goals:** frontend UI, payments, real email/SMS delivery (stubbed senders), video consults, multi-tenancy.

## 3. Personas
| Persona | Can do |
|---|---|
| PATIENT | Self-register, manage own profile, book/cancel own appointments, view own prescriptions |
| DOCTOR | View own schedule, view patients they have appointments with, complete appointments, write prescriptions, search patients |
| ADMIN | Create/update doctors, list all patients, view audit logs, trigger reindex |

## 4. Functional requirements
Each FR is implemented in exactly one day spec.

| ID | Requirement | Day |
|---|---|---|
| FR-1 | Register (patient) and login; login returns a signed JWT with role claim | 1 |
| FR-2 | Role-based access control (PATIENT/DOCTOR/ADMIN) at URL and method level; object-level ownership checks | 1 |
| FR-3 | Patient profile CRUD (own profile; admin/doctor read) | 1 |
| FR-4 | Doctor management (admin create/update; authenticated list/filter by specialty, paginated) | 1 |
| FR-5 | Bean validation and uniform error responses (RFC 7807 ProblemDetail) | 1 |
| FR-6 | Book appointment with slot rules and conflict detection | 2 |
| FR-7 | Appointment lifecycle: BOOKED → CANCELLED / COMPLETED; list own appointments | 2 |
| FR-8 | Prescriptions written by doctor against a completed/booked appointment; patient reads own | 2 |
| FR-9 | Event-driven notifications on booking/cancel (Observer + Factory, stub channels) | 2 |
| FR-10 | AOP audit logging of patient-data access; admin audit log query | 2 |
| FR-11 | Elasticsearch search for doctors (name/specialty/bio) and patients (name) with fuzzy matching; admin reindex | 2 |
| FR-12 | DB portability: PostgreSQL default, MySQL via Spring profile using vendor-specific Flyway scripts | 2 |
| FR-13 | Field-level encryption (AES-GCM) of patient national ID | 2 |
| FR-14 | Automated tests: unit (JUnit 5 + Mockito), slice, Testcontainers integration, JaCoCo coverage gate | 3 |
| FR-15 | Containerisation: multi-stage Dockerfile + compose (app, Postgres, Elasticsearch) | 3 |
| FR-16 | CI: GitHub Actions build + test + coverage on every push/PR | 3 |
| FR-17 | API tooling: Postman collection with assertions, OpenAPI (springdoc), DBeaver ER diagram | 3 |
| FR-18 | Cloud deployment to AWS (EC2 free tier) with public demo URL | 3 |
| FR-19 | README with architecture, run instructions, design-pattern map, AI-tools usage section | 3 |

## 5. Non-functional requirements
| ID | Requirement |
|---|---|
| NFR-1 | Passwords BCrypt (strength ≥10); never logged or serialised |
| NFR-2 | JWT HS256, 1h expiry, secret from env var; stateless sessions |
| NFR-3 | No PII (name, national ID, email) in application logs |
| NFR-4 | List endpoints paginated (default 20, max 100) |
| NFR-5 | p95 < 300 ms for booking and search on seeded data (≈1k doctors, 10k patients) locally |
| NFR-6 | Schema managed only by Flyway; `ddl-auto=validate` |
| NFR-7 | Constructor injection only; strict controller → service → repository layering; DTOs at the API boundary (entities never exposed) |
| NFR-8 | Service-layer line coverage ≥ 70% (JaCoCo) |

## 6. Domain model
```
users(id, email UNIQUE, password_hash, role, enabled, created_at)
patients(id, user_id FK UNIQUE, full_name, dob, phone, national_id_enc, created_at)
doctors(id, user_id FK UNIQUE, full_name, specialty, bio, working_start, working_end, slot_minutes)
appointments(id, patient_id FK, doctor_id FK, start_time, end_time, status, reason, version,
             UNIQUE(doctor_id, start_time))
prescriptions(id, appointment_id FK, medication, dosage, instructions, created_at)
audit_logs(id, actor_user_id, actor_role, action, resource_type, resource_id, created_at)
```
Relations: user 1–1 patient|doctor; doctor 1–N appointments; patient 1–N appointments; appointment 1–N prescriptions.

## 7. API surface (summary)
| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Patients | `GET/PUT /api/patients/me`, `GET /api/patients/{id}`, `GET /api/patients` |
| Doctors | `POST /api/doctors`, `PUT /api/doctors/{id}`, `GET /api/doctors`, `GET /api/doctors/{id}` |
| Appointments | `POST /api/appointments`, `GET /api/appointments/mine`, `PATCH /api/appointments/{id}/cancel`, `PATCH /api/appointments/{id}/complete` |
| Prescriptions | `POST /api/appointments/{id}/prescriptions`, `GET /api/patients/{id}/prescriptions` |
| Search | `GET /api/search/doctors`, `GET /api/search/patients` |
| Admin | `GET /api/admin/audit-logs`, `POST /api/admin/search/reindex` |
| Ops | `GET /actuator/health` |

## 8. Architecture
```
Client/Postman → Security filter chain (JWT) → Controller (DTO, validation)
   → Service (@Transactional, slot policies, events) → Repository (Spring Data JPA) → PostgreSQL|MySQL
                       │                                         
                       ├─ AOP: AuditAspect, TimingAspect
                       └─ Spring events → NotificationListener (Factory → Email/SMS stub)
                                        → SearchIndexListener → Elasticsearch
```
Package layout: `com.medislot.{config, security, auth, patient, doctor, appointment, prescription, notification, search, audit, common}`.

## 9. Design-pattern map (interview talking points)
| Pattern | Where |
|---|---|
| Strategy | `SlotPolicy` implementations (working hours, overlap, minimum lead time) |
| Factory | `NotificationSenderFactory` → `EmailSender` / `SmsSender` |
| Builder | Response DTOs / `Notification` |
| Observer | `AppointmentBookedEvent` / `AppointmentCancelledEvent` listeners |
| Proxy (via Spring AOP) | `AuditAspect`, `TimingAspect`, `@Transactional` |
| Repository / DTO / Layered | Throughout |

## 10. Job-post traceability
| Job-post line | Covered by |
|---|---|
| OOP + Java design patterns | §9, FR-6, FR-9 |
| Spring Boot, Security, Data, AOP, DI | FR-1/2/3/4, FR-10, NFR-7 |
| MySQL / PostgreSQL / Oracle | FR-12 (+ Oracle XE stretch in Day 3) |
| Elasticsearch | FR-11 |
| IntelliJ IDEA | Project opens as standard Maven project; `.idea` run configs not required |
| Git / cloud | FR-16, FR-18, clean conventional commits |
| JUnit, Mockito | FR-14 |
| Postman, DBeaver | FR-17 |
| Security best practices / data protection | FR-2, FR-10, FR-13, NFR-1–3 |
| Agile / SDLC | PRD → spec → plan → implementation → CI → deploy; daily DoD |
| AI tools (Cursor, Kiro, Antigravity, Warp, Codex CLI) | FR-19 |

## 11. Risks
| Risk | Mitigation |
|---|---|
| 3 days is tight | Day 2 stretch items (MySQL profile, encryption) can slip to Day 3 morning; Oracle is explicitly optional |
| Elasticsearch heavy on laptop/EC2 | Single-node, `ES_JAVA_OPTS=-Xms512m -Xmx512m`, security disabled in dev; t3.small+ for demo |
| Double booking under concurrency | DB unique constraint + `@Version` + overlap query inside transaction; test it |
| Flyway differs per vendor | `db/migration/{vendor}` folders, keep schema small |
| AOP self-invocation silently skips audit | Audit at controller-facing service boundary only; test that aspect fires |

## 12. Success criteria
- `docker compose up` → healthy stack; Postman collection runs 100% green.
- `./mvnw verify` green locally and in GitHub Actions, coverage gate passes.
- Public demo URL live; README lets a stranger run it in < 5 minutes.
- Author can explain every design choice in the interview.
