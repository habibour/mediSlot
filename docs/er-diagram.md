# Entity-relationship diagram

Derived from the Flyway migrations (`src/main/resources/db/migration/postgresql/V1`–`V3`; the MySQL scripts define the same tables).
This is a hand-maintained Mermaid diagram, not an export from a database tool; it renders on GitHub.

```mermaid
erDiagram
    USERS ||--o| PATIENTS : "has profile"
    USERS ||--o| DOCTORS : "has profile"
    PATIENTS ||--o{ APPOINTMENTS : books
    DOCTORS ||--o{ APPOINTMENTS : "is booked for"
    APPOINTMENTS ||--o{ PRESCRIPTIONS : "results in"
    USERS ||--o{ AUDIT_LOGS : "performs (actor_user_id)"

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash "BCrypt"
        varchar role "PATIENT | DOCTOR | ADMIN"
        boolean enabled
        timestamptz created_at
    }
    PATIENTS {
        bigint id PK
        bigint user_id FK,UK
        varchar full_name
        date dob
        varchar phone
        varchar national_id_enc "AES-256-GCM ciphertext"
        timestamptz created_at
    }
    DOCTORS {
        bigint id PK
        bigint user_id FK,UK
        varchar full_name
        varchar specialty
        text bio
        time working_start "clinic-local"
        time working_end "clinic-local"
        int slot_minutes
    }
    APPOINTMENTS {
        bigint id PK
        bigint patient_id FK
        bigint doctor_id FK
        timestamptz start_time "UTC"
        timestamptz end_time "UTC"
        varchar status "BOOKED | CANCELLED | COMPLETED"
        varchar reason
        bigint version "optimistic lock"
        timestamptz created_at
    }
    PRESCRIPTIONS {
        bigint id PK
        bigint appointment_id FK
        varchar medication
        varchar dosage
        text instructions
        timestamptz created_at
    }
    AUDIT_LOGS {
        bigint id PK
        bigint actor_user_id
        varchar actor_role
        varchar action
        varchar resource_type
        bigint resource_id
        timestamptz created_at
    }
```

Notable constraints:

- `appointments`: partial unique index on `(doctor_id, start_time) WHERE status = 'BOOKED'` (MySQL: generated column `active_start` + unique index), so a cancelled slot can be booked again.
- `appointments.end_time > start_time`, `doctors.working_start < working_end`, `doctors.slot_minutes BETWEEN 5 AND 240` are enforced by `CHECK` constraints.
- `audit_logs.actor_user_id` is deliberately not a foreign key: the trail must survive user changes.
