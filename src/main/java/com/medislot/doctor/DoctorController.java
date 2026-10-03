package com.medislot.doctor;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.common.dto.PageResponse;
import com.medislot.doctor.dto.CreateDoctorRequest;
import com.medislot.doctor.dto.DoctorRequest;
import com.medislot.doctor.dto.DoctorResponse;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService service;

    public DoctorController(DoctorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse create(@Valid @RequestBody CreateDoctorRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return service.update(id, request);
    }

    @GetMapping
    public PageResponse<DoctorResponse> list(
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return PageResponse.of(service.list(specialty, PageResponse.pageable(page, size, Sort.by("id"))),
                DoctorResponse::from);
    }

    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
