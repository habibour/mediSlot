# Day 1 Plan — Foundation, Domain, Security

**Implements:** `day1-spec.md` · **Budget:** ~10 h · Every task lists the acceptance criteria it satisfies.

## Block 1 — Skeleton & database (1.5 h) → D1-AC1, D1-AC2
- [ ] 1.1 Generate project (start.spring.io or IntelliJ): group `com.medislot`, artifact `medislot`, Java 21, Maven, dependencies per spec. `git init`, `.gitignore`, first commit.
- [ ] 1.2 `docker-compose.dev.yml` with only Postgres 16 (`medislot` db, port 5432, volume).
- [ ] 1.3 `application.yml` (shared), `application-dev.yml` (dev DB creds, dev JWT secret), flyway `locations: classpath:db/migration/{vendor}`, `ddl-auto: validate`, `open-in-view: false`.
- [ ] 1.4 Write `db/migration/postgresql/V1__init.sql` (users, patients, doctors, indexes on `users.email`, `doctors.specialty`) and `V2__seed_admin.sql`.
- [ ] 1.5 Entities `User`, `Patient`, `Doctor`, enum `Role`; repositories.
- **Checkpoint:** `./mvnw spring-boot:run` starts; `curl localhost:8080/actuator/health` → UP; connect with DBeaver and see tables.

## Block 2 — Common layer (1 h) → D1-AC8, D1-AC10
- [ ] 2.1 `common/exception`: `ResourceNotFoundException`, `ConflictException`, `GlobalExceptionHandler` (`@RestControllerAdvice`) returning `ProblemDetail`; handle `MethodArgumentNotValidException` (field `errors` map), `AccessDeniedException`, generic `Exception` (log id, no stack to client).
- [ ] 2.2 `common/dto`: `PageResponse<T>` wrapper; pagination helper capping size at 100.
- [ ] 2.3 Use Java records for request/response DTOs; MapStruct optional — manual mappers are fine and easier to explain.
- **Checkpoint:** a throwaway endpoint with a bad body returns the expected 400 shape.

## Block 3 — Auth & security (3 h) → D1-AC3, D1-AC4, D1-AC5, D1-AC6, D1-AC9
- [ ] 3.1 `security/JwtService`: generate/validate HS256 token (subject = userId, claims `role`, `email`), expiry from config.
- [ ] 3.2 `security/JwtAuthenticationFilter` (`OncePerRequestFilter`): parse `Authorization: Bearer`, set `SecurityContext`.
- [ ] 3.3 `security/SecurityConfig`: stateless, CSRF off for API, `PasswordEncoder` = BCrypt, permit `/api/auth/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`; all else authenticated; `@EnableMethodSecurity`.
- [ ] 3.4 Custom `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) writing ProblemDetail JSON.
- [ ] 3.5 `auth/AuthService` + `AuthController`: register (user + patient in one `@Transactional`), login (`AuthenticationManager` or manual BCrypt match; same message for unknown email/wrong password).
- [ ] 3.6 `security/CurrentUser` helper (resolve userId/role from `SecurityContext`).
- [ ] 3.7 Ensure `User.passwordHash` is excluded everywhere (never in DTOs); add a log statement check — grep logs for email after a run.
- **Checkpoint:** Postman: register → login → call protected endpoint with and without token; wrong-role call gives 403.

## Block 4 — Patient & doctor modules (2.5 h) → D1-AC6, D1-AC7
- [ ] 4.1 `patient`: `PatientService` (`getMe`, `updateMe`, `getById`, `list`), `PatientController` with `@PreAuthorize` per spec table. `/me` uses `CurrentUser`.
- [ ] 4.2 `doctor`: `DoctorService` (`create` makes user+doctor transactionally, `update`, `get`, `list(specialty, pageable)`), `DoctorController`; `@PreAuthorize("hasRole('ADMIN')")` on writes.
- [ ] 4.3 Derived query `findBySpecialtyIgnoreCase(String, Pageable)`.
- [ ] 4.4 Add `springdoc` config with bearer security scheme so Swagger UI can authorise.
- **Checkpoint:** run full Day 1 endpoint table in Postman as ADMIN, DOCTOR, PATIENT; confirm 200/403 matrix.

## Block 5 — Tests & wrap-up (1.5 h) → D1-AC3–D1-AC6, DoD
- [ ] 5.1 Mockito unit tests: `AuthServiceTest` (duplicate email → conflict, hash is not plaintext, bad login), `JwtServiceTest` (valid, expired, tampered).
- [ ] 5.2 `@WebMvcTest` for `PatientController` security: 401 without token, 403 as DOCTOR on `/me`.
- [ ] 5.3 Start Postman collection `MediSlot.postman_collection.json` with Day 1 folders and environment variables (`baseUrl`, `token`).
- [ ] 5.4 `./mvnw verify`; commit as `feat: auth, patients, doctors`; push.
- [ ] 5.5 Write 5 bullets in `docs/notes.md`: things you'd be asked about (filter chain order, why stateless, why DTOs, BCrypt vs SHA, ProblemDetail).

## Traceability
| Spec criterion | Plan tasks |
|---|---|
| D1-AC1 | 1.1–1.3 |
| D1-AC2 | 1.3, 1.4, 1.5 |
| D1-AC3 | 3.5, 5.1 |
| D1-AC4 | 3.1, 3.5, 5.1 |
| D1-AC5 | 3.2, 3.3, 3.4, 5.2 |
| D1-AC6 | 3.6, 4.1, 5.2 |
| D1-AC7 | 4.2, 4.3 |
| D1-AC8 | 2.1 |
| D1-AC9 | 3.7 |
| D1-AC10 | 2.3, 4.1, 4.2 |

## If you fall behind
Cut order: springdoc (4.4) → `@WebMvcTest` (5.2, move to Day 3) → `PageResponse` wrapper (return `Page` directly).
