package com.medislot.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.medislot.support.AbstractIntegrationTest;

@TestPropertySource(properties = {"app.search.enabled=true", "management.health.elasticsearch.enabled=true"})
class SearchIT extends AbstractIntegrationTest {

    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.18.3")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static {
        ES.start();
    }

    @DynamicPropertySource
    static void esProps(DynamicPropertyRegistry r) {
        r.add("spring.elasticsearch.uris", () -> "http://" + ES.getHttpHostAddress());
    }

    @Autowired ElasticsearchOperations operations;

    private static final AtomicInteger N = new AtomicInteger();

    /** Letters only and unlikely to be within edit distance 2 of any other token used in this class. */
    private static String token() {
        StringBuilder sb = new StringBuilder("Zq");
        for (char c : Integer.toString(N.incrementAndGet() * 7919 + 100_003, 26).toCharArray()) {
            sb.append((char) (Character.isDigit(c) ? 'a' + (c - '0') : c + 10));
        }
        return sb + "x";
    }

    private JsonNode searchDoctors(String token, String query) {
        return call(HttpMethod.GET, "/api/search/doctors?q=" + query, token, null).getBody();
    }

    @Test
    void healthReportsElasticsearchUp() {
        assertThat(call(HttpMethod.GET, "/actuator/health", null, null).getBody().get("status").asText())
                .isEqualTo("UP");
    }

    @Test
    void newDoctorIsSearchableAndTyposStillMatch() {
        String name = token() + "ovich";
        DoctorFixture doctor = createDoctor("Dr " + name, "Cardiology");
        String patient = registerPatient();
        String typo = name.substring(0, name.length() - 3) + "ovch"; // 1 deletion

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode exact = searchDoctors(patient, name);
            assertThat(exact).hasSize(1);
            assertThat(exact.get(0).get("id").asLong()).isEqualTo(doctor.id());
            assertThat(searchDoctors(patient, typo)).extracting(n -> n.get("id").asLong()).contains(doctor.id());
        });
    }

    @Test
    void specialtyFilterAndSpecialtyTextMatchWork() {
        String spec = token() + "ology";
        DoctorFixture doctor = createDoctor("Dr " + token(), spec);
        createDoctor("Dr " + token(), "Cardiology");
        String patient = registerPatient();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(searchDoctors(patient, spec)).extracting(n -> n.get("id").asLong()).containsExactly(doctor.id());
            JsonNode filtered = call(HttpMethod.GET, "/api/search/doctors?q=Dr&specialty=" + spec, patient, null).getBody();
            assertThat(filtered).extracting(n -> n.get("id").asLong()).containsExactly(doctor.id());
        });
    }

    @Test
    void updatingADoctorUpdatesTheIndex() {
        DoctorFixture doctor = createDoctor("Dr " + token(), "Cardiology");
        String newSpecialty = token() + "ics";
        call(HttpMethod.PUT, "/api/doctors/" + doctor.id(), adminToken(), Map.of("fullName", "Dr Renamed",
                "specialty", newSpecialty, "workingStart", "09:00", "workingEnd", "17:00", "slotMinutes", 30));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(searchDoctors(registerPatient(), newSpecialty))
                        .extracting(n -> n.get("id").asLong()).containsExactly(doctor.id()));
    }

    @Test
    void patientSearchIsRoleRestrictedAndDoctorsOnlySeeTheirOwnPatients() {
        DoctorFixture doctor = createDoctor();
        String common = token();
        String linked = registerPatient(common + "alpha", Map.of());
        registerPatient(common + "beta", Map.of());
        book(linked, doctor.id(), slot(2, 10, 0));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode admin = call(HttpMethod.GET, "/api/search/patients?q=" + common + "alpha", adminToken(), null).getBody();
            assertThat(admin).hasSize(1);
        });
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode adminAll = call(HttpMethod.GET, "/api/search/patients?q=" + common + "alpha", adminToken(), null).getBody();
            assertThat(adminAll).extracting(n -> n.get("fullName").asText()).contains(common + "alpha");
            // fuzzy: "beta" vs "alpha" are far apart, so query for each explicitly
            JsonNode betaAdmin = call(HttpMethod.GET, "/api/search/patients?q=" + common + "beta", adminToken(), null).getBody();
            assertThat(betaAdmin).hasSize(1);
            JsonNode betaDoctor = call(HttpMethod.GET, "/api/search/patients?q=" + common + "beta", doctor.token(), null).getBody();
            assertThat(betaDoctor).isEmpty();
            JsonNode alphaDoctor = call(HttpMethod.GET, "/api/search/patients?q=" + common + "alpha", doctor.token(), null).getBody();
            assertThat(alphaDoctor).hasSize(1);
        });
        assertThat(call(HttpMethod.GET, "/api/search/patients?q=x", registerPatient(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void searchRequiresAuthenticationAndAQuery() {
        assertThat(call(HttpMethod.GET, "/api/search/doctors?q=x", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/search/doctors?q=", registerPatient(), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/api/search/doctors", registerPatient(), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void indexNeverContainsPiiBeyondTheName() {
        String name = token() + "pii";
        String email = "pii-" + System.nanoTime() + "@leak.test";
        call(HttpMethod.POST, "/api/auth/register", null, Map.of("email", email, "password", PASSWORD,
                "fullName", name, "phone", "01799988877", "dob", "1990-05-17", "nationalId", "NID998877665"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String raw = RestClient.create().get()
                    .uri("http://" + ES.getHttpHostAddress() + "/" + PatientDocument.INDEX + "/_search?size=1000")
                    .retrieve().body(String.class);
            assertThat(raw).contains(name);
            assertThat(raw).doesNotContain(email).doesNotContain("01799988877").doesNotContain("1990-05-17")
                    .doesNotContain("NID998877665").doesNotContain("leak.test");
        });
    }

    @Test
    void reindexRebuildsEverythingFromTheDatabaseAfterTheIndexIsLost() {
        String name = token() + "heal";
        DoctorFixture doctor = createDoctor("Dr " + name, "Cardiology");
        String patient = registerPatient();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(searchDoctors(patient, name)).hasSize(1));

        operations.indexOps(DoctorDocument.class).delete();
        operations.indexOps(PatientDocument.class).delete();
        assertThat(call(HttpMethod.POST, "/api/admin/search/reindex", registerPatient(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode result = call(HttpMethod.POST, "/api/admin/search/reindex", adminToken(), null).getBody();

        assertThat(result.get("doctors").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(result.get("patients").asLong()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(searchDoctors(patient, name)).extracting(n -> n.get("id").asLong()).containsExactly(doctor.id()));
    }
}
