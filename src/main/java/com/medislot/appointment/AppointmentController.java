package com.medislot.appointment;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.appointment.dto.AppointmentResponse;
import com.medislot.appointment.dto.BookAppointmentRequest;
import com.medislot.common.dto.PageResponse;
import com.medislot.security.CurrentUser;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request) {
        return service.book(CurrentUser.get().userId(), request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public PageResponse<AppointmentResponse> mine(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        var pageable = PageResponse.pageable(page, size, Sort.by("startTime").descending());
        return PageResponse.of(service.mine(CurrentUser.get(), status, pageable), AppointmentResponse::from);
    }

    @PatchMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id) {
        return service.cancel(CurrentUser.get(), id);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public AppointmentResponse complete(@PathVariable Long id) {
        return service.complete(CurrentUser.get(), id);
    }
}
