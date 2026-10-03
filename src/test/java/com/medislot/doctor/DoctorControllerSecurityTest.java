package com.medislot.doctor;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medislot.security.JwtService;
import com.medislot.security.SecurityTestConfig;
import com.medislot.user.Role;

@WebMvcTest(DoctorController.class)
@Import(SecurityTestConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234", "app.jwt.expiration-seconds=3600"})
class DoctorControllerSecurityTest {

    private static final String VALID_CREATE = """
            {"email":"d@x.com","password":"Passw0rd1","profile":{"fullName":"Dr","specialty":"Cardio",
             "workingStart":"09:00","workingEnd":"17:00","slotMinutes":30}}""";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean DoctorService service;

    private String bearer(Role role) {
        return "Bearer " + jwt.generate(1L, role);
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/doctors")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCanCreateDoctors() throws Exception {
        for (Role role : new Role[] {Role.PATIENT, Role.DOCTOR}) {
            mvc.perform(post("/api/doctors").header(HttpHeaders.AUTHORIZATION, bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void onlyAdminCanUpdateDoctors() throws Exception {
        mvc.perform(put("/api/doctors/1").header(HttpHeaders.AUTHORIZATION, bearer(Role.PATIENT))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidCreateBodyIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/doctors").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.profile").exists());
    }

    @Test
    void oversizedPageIsClampedToMax() throws Exception {
        org.mockito.Mockito.when(service.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mvc.perform(get("/api/doctors?size=100000").header(HttpHeaders.AUTHORIZATION, bearer(Role.PATIENT)))
                .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(service).list(org.mockito.ArgumentMatchers.any(), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
