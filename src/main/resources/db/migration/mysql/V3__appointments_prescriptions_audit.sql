CREATE TABLE appointments (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id   BIGINT       NOT NULL,
    doctor_id    BIGINT       NOT NULL,
    start_time   DATETIME(6)  NOT NULL,
    end_time     DATETIME(6)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    reason       VARCHAR(500),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- MySQL has no partial indexes: NULL for non-BOOKED rows lets a unique index ignore them
    active_start DATETIME(6) GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED' THEN start_time ELSE NULL END) STORED,
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors (id),
    CONSTRAINT ck_appt_status  CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_appt_range   CHECK (end_time > start_time)
) ENGINE = InnoDB;

CREATE UNIQUE INDEX uq_appt_doctor_active_start ON appointments (doctor_id, active_start);
CREATE INDEX idx_appt_doctor_start ON appointments (doctor_id, start_time);
CREATE INDEX idx_appt_patient_start ON appointments (patient_id, start_time);

CREATE TABLE prescriptions (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT       NOT NULL,
    medication     VARCHAR(200) NOT NULL,
    dosage         VARCHAR(100) NOT NULL,
    instructions   TEXT,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_rx_appt FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE = InnoDB;

CREATE TABLE audit_logs (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT      NOT NULL,
    actor_role    VARCHAR(20) NOT NULL,
    action        VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id   BIGINT,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB;
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id, created_at);
CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
