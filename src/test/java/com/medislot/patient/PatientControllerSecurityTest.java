package com.medislot.patient;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medislot.patient.dto.PatientResponse;
import com.medislot.security.JwtService;
import com.medislot.security.SecurityTestConfig;
import com.medislot.user.Role;

@WebMvcTest(PatientController.class)
@Import(SecurityTestConfig.class)
@TestPropertySource(properties = {
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234", "app.jwt.expiration-seconds=3600"})
class PatientControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean PatientService service;

    private String bearer(long userId, Role role) {
        return "Bearer " + jwt.generate(userId, role);
    }

    @Test
    void noTokenIs401WithProblemJson() throws Exception {
        mvc.perform(get("/api/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        verifyNoInteractions(service);
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mvc.perform(get("/api/patients/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doctorCannotUseMeEndpoint() throws Exception {
        mvc.perform(get("/api/patients/me").header(HttpHeaders.AUTHORIZATION, bearer(5, Role.DOCTOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verifyNoInteractions(service);
    }

    @Test
    void meResolvesIdentityFromTokenNotFromRequest() throws Exception {
        when(service.getMe(7L)).thenReturn(new PatientResponse(1L, "Pat", null, null, null));

        mvc.perform(get("/api/patients/me").header(HttpHeaders.AUTHORIZATION, bearer(7, Role.PATIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Pat"));
        verify(service).getMe(7L);
    }

    @Test
    void patientCannotReadAnotherPatientOrList() throws Exception {
        mvc.perform(get("/api/patients/1").header(HttpHeaders.AUTHORIZATION, bearer(7, Role.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, bearer(7, Role.PATIENT)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void doctorCanReadPatientById() throws Exception {
        when(service.getById(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(new PatientResponse(1L, "Pat", null, null, null));

        mvc.perform(get("/api/patients/1").header(HttpHeaders.AUTHORIZATION, bearer(5, Role.DOCTOR)))
                .andExpect(status().isOk());
    }
}
