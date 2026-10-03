package com.medislot.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.medislot.common.event.DoctorChangedEvent;
import com.medislot.common.event.PatientChangedEvent;

/**
 * Keeps the index in step with the database after commit. Indexing is best-effort: the database is the source of
 * truth and {@code POST /api/admin/search/reindex} heals any gap, so a search outage must not fail a write.
 */
@Component
@ConditionalOnSearchEnabled
public class SearchIndexListener {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexListener.class);

    private final ElasticsearchOperations operations;
    private final SearchIndexManager indices;

    public SearchIndexListener(ElasticsearchOperations operations, SearchIndexManager indices) {
        this.operations = operations;
        this.indices = indices;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctor(DoctorChangedEvent e) {
        index(new DoctorDocument(e.doctorId(), e.fullName(), e.specialty(), e.bio()), "doctor", e.doctorId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatient(PatientChangedEvent e) {
        index(new PatientDocument(e.patientId(), e.fullName()), "patient", e.patientId());
    }

    private void index(Object document, String kind, Long id) {
        try {
            indices.ensureIndices();
            operations.save(document);
        } catch (RuntimeException ex) {
            log.error("Failed to index {} id={}; run the admin reindex to repair", kind, id, ex);
        }
    }
}
