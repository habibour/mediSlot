package com.medislot.search;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.common.dto.PageResponse;
import com.medislot.doctor.DoctorService;
import com.medislot.doctor.dto.DoctorResponse;
import com.medislot.patient.PatientService;
import com.medislot.patient.dto.PatientResponse;
import com.medislot.security.CurrentUser;

@RestController
@Validated
@ConditionalOnSearchEnabled
public class SearchController {

    private final SearchService search;
    private final ReindexService reindexService;
    private final DoctorService doctors;
    private final PatientService patients;

    public SearchController(SearchService search, ReindexService reindexService, DoctorService doctors,
            PatientService patients) {
        this.search = search;
        this.reindexService = reindexService;
        this.doctors = doctors;
        this.patients = patients;
    }

    @GetMapping("/api/search/doctors")
    public List<DoctorResponse> searchDoctors(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 100) String specialty,
            @RequestParam(defaultValue = "20") int size) {
        return doctors.getByIds(search.searchDoctors(q.trim(), specialty, size));
    }

    /** Hits are hydrated through PatientService, so a doctor only ever sees their own patients. */
    @GetMapping("/api/search/patients")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public List<PatientResponse> searchPatients(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "20") int size) {
        return patients.hydrateForSearch(CurrentUser.get(), search.searchPatients(q.trim(), size));
    }

    @PostMapping("/api/admin/search/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ReindexService.Result reindex() {
        return reindexService.reindex();
    }
}
