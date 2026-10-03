package com.medislot.search;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import com.medislot.doctor.Doctor;
import com.medislot.doctor.DoctorRepository;
import com.medislot.patient.Patient;
import com.medislot.patient.PatientRepository;

@Service
@ConditionalOnSearchEnabled
public class ReindexService {

    static final int BATCH = 500;

    private final ElasticsearchOperations operations;
    private final SearchIndexManager indices;
    private final DoctorRepository doctors;
    private final PatientRepository patients;

    public ReindexService(ElasticsearchOperations operations, SearchIndexManager indices,
            DoctorRepository doctors, PatientRepository patients) {
        this.operations = operations;
        this.indices = indices;
        this.doctors = doctors;
        this.patients = patients;
    }

    public record Result(long doctors, long patients) {
    }

    /** Rebuilds both indices from the database, streaming in batches so memory stays flat. */
    public Result reindex() {
        indices.recreateIndices();
        long doctorCount = 0;
        Page<Doctor> dp;
        int page = 0;
        do {
            dp = doctors.findAll(PageRequest.of(page++, BATCH, Sort.by("id")));
            List<DoctorDocument> docs = dp.getContent().stream()
                    .map(d -> new DoctorDocument(d.getId(), d.getFullName(), d.getSpecialty(), d.getBio())).toList();
            if (!docs.isEmpty()) {
                operations.save(docs);
            }
            doctorCount += docs.size();
        } while (dp.hasNext());

        long patientCount = 0;
        Page<Patient> pp;
        page = 0;
        do {
            pp = patients.findAll(PageRequest.of(page++, BATCH, Sort.by("id")));
            List<PatientDocument> docs = pp.getContent().stream()
                    .map(p -> new PatientDocument(p.getId(), p.getFullName())).toList();
            if (!docs.isEmpty()) {
                operations.save(docs);
            }
            patientCount += docs.size();
        } while (pp.hasNext());
        return new Result(doctorCount, patientCount);
    }
}
