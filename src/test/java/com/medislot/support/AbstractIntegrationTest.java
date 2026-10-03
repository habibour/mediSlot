package com.medislot.support;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;

/** Boots the full app once per JVM on a shared throwaway database; helpers drive the real HTTP API. */
/** Search is off by default so these tests need no Elasticsearch node; SearchIT switches it back on. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"app.search.enabled=false", "management.health.elasticsearch.enabled=false"})
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rd1";
    protected static final ZoneId CLINIC = ZoneId.of("Asia/Dhaka");
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Postgres by default; {@code ./mvnw verify -Dtest.db=mysql} runs the very same suite against MySQL. */
    static final JdbcDatabaseContainer<?> DB = "mysql".equals(System.getProperty("test.db"))
            ? new MySQLContainer<>("mysql:8.4")
            : new PostgreSQLContainer<>("postgres:16");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("app.encryption.key", () -> "ZGV2LW9ubHktMzItYnl0ZS1rZXktZm9yLWFlcy0yNTY=");
        r.add("app.jwt.secret", () -> "integration-test-secret-integration-test-secret");
    }

    @Autowired protected TestRestTemplate rest;

    protected static String uniqueEmail(String prefix) {
        return prefix + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected String login(String email, String password) {
        return call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", email, "password", password))
                .getBody().get("token").asText();
    }

    protected String adminToken() {
        return login("admin@medislot.local", "Admin@12345");
    }

    /** Registers a patient and returns their token. */
    protected String registerPatient() {
        return registerPatient("Patient " + uniqueEmail("p"), Map.of());
    }

    protected String registerPatient(String fullName, Map<String, Object> extraFields) {
        String email = uniqueEmail("patient");
        Map<String, Object> body = new java.util.HashMap<>(extraFields);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("fullName", fullName);
        call(HttpMethod.POST, "/api/auth/register", null, body);
        return login(email, PASSWORD);
    }

    protected record DoctorFixture(long id, String email, String token) {
    }

    /** Admin creates a doctor working 09:00-17:00 clinic time in 30-minute slots. */
    protected DoctorFixture createDoctor() {
        return createDoctor("Dr " + uniqueEmail("d"), "Cardiology");
    }

    protected DoctorFixture createDoctor(String fullName, String specialty) {
        String email = uniqueEmail("doctor");
        Map<String, Object> profile = Map.of("fullName", fullName, "specialty", specialty,
                "workingStart", "09:00", "workingEnd", "17:00", "slotMinutes", 30);
        ResponseEntity<JsonNode> res = call(HttpMethod.POST, "/api/doctors", adminToken(),
                Map.of("email", email, "password", PASSWORD, "profile", profile));
        return new DoctorFixture(res.getBody().get("id").asLong(), email, login(email, PASSWORD));
    }

    /** ISO-8601 instant (with offset) for clinic-local {@code hour:minute}, {@code daysAhead} days from now. */
    protected static String slot(int daysAhead, int hour, int minute) {
        return ZonedDateTime.now(CLINIC).plusDays(daysAhead).withHour(hour).withMinute(minute)
                .withSecond(0).withNano(0).toOffsetDateTime().toString();
    }

    protected ResponseEntity<JsonNode> book(String patientToken, long doctorId, String startTime) {
        return call(HttpMethod.POST, "/api/appointments", patientToken,
                Map.of("doctorId", doctorId, "startTime", startTime, "reason", "checkup"));
    }
}
