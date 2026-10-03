# Day 1 Spec — Foundation, Domain, Security

**Covers:** FR-1, FR-2, FR-3, FR-4, FR-5 · NFR-1, NFR-2, NFR-3, NFR-4, NFR-6, NFR-7
**Companion:** `day1-plan.md`

## Scope
A bootable Spring Boot app on PostgreSQL with Flyway-managed schema, users/patients/doctors, JWT authentication, role- and ownership-based authorisation, validation and uniform errors.

Out of scope today: appointments, prescriptions, AOP, search (Day 2).

## Technical decisions
- Maven, Java 21, Spring Boot 3.x. Dependencies: web, validation, security, data-jpa, actuator, postgresql, flyway-core + flyway-database-postgresql, jjwt (0.12.x), lombok, springdoc-openapi, test starters.
- Flyway locations: `classpath:db/migration/{vendor}` (so MySQL scripts can be added on Day 2). Day 1 writes `db/migration/postgresql/`.
- `spring.jpa.hibernate.ddl-auto=validate`; `open-in-view=false`.
- Secrets (`JWT_SECRET`, DB creds) from env vars with dev defaults only in `application-dev.yml`.
- Error format: RFC 7807 `ProblemDetail` via `@RestControllerAdvice`, with `errors` map for field violations.

## Data model (Flyway V1, V2)
- `V1__init.sql`: `users`, `patients`, `doctors` per PRD §6 (appointments/prescriptions/audit come Day 2).
- `V2__seed_admin.sql`: one ADMIN user (BCrypt hash precomputed; password documented in README for dev only).

## Endpoints
| Method & path | Role | Behaviour |
|---|---|---|
| `POST /api/auth/register` | public | Creates user (role PATIENT) + patient profile; 201 |
| `POST /api/auth/login` | public | Returns `{token, tokenType, expiresIn, role}` |
| `GET /api/patients/me` | PATIENT | Own profile |
| `PUT /api/patients/me` | PATIENT | Update own profile (name, phone, dob) |
| `GET /api/patients/{id}` | DOCTOR, ADMIN | Any patient (doctor restriction to own patients arrives Day 2) |
| `GET /api/patients` | ADMIN | Paginated list |
| `POST /api/doctors` | ADMIN | Creates user (role DOCTOR) + doctor profile |
| `PUT /api/doctors/{id}` | ADMIN | Update doctor |
| `GET /api/doctors` | any authenticated | Paginated; optional `specialty` filter |
| `GET /api/doctors/{id}` | any authenticated | Single doctor |
| `GET /actuator/health` | public | Liveness |

## Acceptance criteria
| ID | Criterion | FR/NFR |
|---|---|---|
| D1-AC1 | App starts against Postgres; `/actuator/health` returns `UP` | — |
| D1-AC2 | Flyway creates schema; Hibernate `validate` passes; no manual DDL | NFR-6 |
| D1-AC3 | Register stores BCrypt hash; duplicate email → 409; weak/missing fields → 400 | FR-1, NFR-1 |
| D1-AC4 | Login with valid creds returns JWT (role claim, 1h expiry); bad creds → 401 | FR-1, NFR-2 |
| D1-AC5 | No/invalid/expired token → 401; valid token wrong role → 403 (both as ProblemDetail JSON) | FR-2 |
| D1-AC6 | Patient cannot read another patient's data (403); `/me` always resolves from the token, never from a client-supplied id | FR-2, FR-3 |
| D1-AC7 | Admin creates doctor; doctor list is paginated (default 20, max 100) and filterable by specialty | FR-4, NFR-4 |
| D1-AC8 | Invalid payloads return 400 ProblemDetail with per-field `errors`; unknown id → 404; uncaught → 500 without stack trace | FR-5 |
| D1-AC9 | Password hash is never in any response or log line; logs contain no email/name | NFR-1, NFR-3 |
| D1-AC10 | Constructor injection only; entities never returned from controllers (DTOs/records) | NFR-7 |

## Definition of done (Day 1)
- [ ] All D1-AC pass via manual Postman run (collection started, exported Day 3)
- [ ] At least 6 service/controller unit tests exist (auth + patient) — full suite is Day 3
- [ ] `./mvnw verify` passes
- [ ] Committed in small conventional commits; pushed to GitHub
