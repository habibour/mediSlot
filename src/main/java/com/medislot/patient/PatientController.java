package com.medislot.patient;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.common.dto.PageResponse;
import com.medislot.patient.dto.PatientResponse;
import com.medislot.patient.dto.UpdatePatientRequest;
import com.medislot.security.CurrentUser;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse me() {
        return service.getMe(CurrentUser.get().userId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientResponse updateMe(@Valid @RequestBody UpdatePatientRequest request) {
        return service.updateMe(CurrentUser.get().userId(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public PatientResponse get(@PathVariable Long id) {
        return service.getById(CurrentUser.get(), id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<PatientResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return PageResponse.of(service.list(PageResponse.pageable(page, size, Sort.by("id"))),
                PatientResponse::from);
    }
}
