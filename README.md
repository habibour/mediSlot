# MediSlot

Secure appointment-booking and records REST API for a clinic, built with **Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Spring AOP, PostgreSQL / MySQL and Elasticsearch**.

> Status: core API complete (auth, booking, prescriptions, audit, search, field encryption). Docker packaging, CI, Postman collection and cloud deployment are planned (see `docs/day3-*.md`).

## What it does
- **Roles & security** — patient / doctor / admin; stateless JWT, BCrypt, URL-level and method-level authorisation, object-level ownership checks, RFC 7807 error responses.
- **Booking without double-booking** — slot policies (Strategy), pessimistic row locks on patient then doctor, partial unique index as a backstop. Verified by concurrent-request tests (20 racing requests → exactly one booking).
- **Audit trail (AOP)** — `@Audited` aspect records every successful access to patient data; admin query endpoint with filters.
- **Events** — Observer via `@TransactionalEventListener(AFTER_COMMIT)`: notifications (Factory over Email/SMS stubs) and Elasticsearch indexing.
- **Search** — typo-tolerant doctor and patient search on Elasticsearch; the index holds names only; admin reindex endpoint.
- **Data protection** — national ID encrypted at rest (AES-256-GCM), returned masked; no PII in logs.
- **Databases** — Flyway migrations for PostgreSQL and MySQL; the same test suite passes on both.

## Run it
```bash
docker compose -f docker-compose.dev.yml up -d          # Postgres + Elasticsearch
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run       # http://localhost:8081, Swagger UI at /swagger-ui.html
```
Add `,seed` for 1k doctors / 10k patients, `,mysql` (with `docker-compose.mysql.yml`) to use MySQL.
Dev-only login: `admin@medislot.local` / `Admin@12345` (seeded by a dev migration — change before any real deployment).

## Test it
```bash
./mvnw verify                      # 83 tests, Testcontainers (Postgres, Elasticsearch)
./mvnw verify -Dtest.db=mysql      # same suite on MySQL 8.4
```

## Docs
`docs/prd.md` (requirements) · `docs/day1-*` / `day2-*` / `day3-*` (spec + plan per day) · `docs/notes.md` (measurements and gotchas).
