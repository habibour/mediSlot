package com.medislot.prescription;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.common.dto.PageResponse;
import com.medislot.prescription.dto.CreatePrescriptionRequest;
import com.medislot.prescription.dto.PrescriptionResponse;
import com.medislot.security.CurrentUser;

@RestController
public class PrescriptionController {

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @PostMapping("/api/appointments/{appointmentId}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse add(@PathVariable Long appointmentId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        return service.add(CurrentUser.get(), appointmentId, request);
    }

    @GetMapping("/api/patients/{patientId}/prescriptions")
    public PageResponse<PrescriptionResponse> list(@PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        var pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(service.listForPatient(CurrentUser.get(), patientId, pageable),
                PrescriptionResponse::from);
    }
}
