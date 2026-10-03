package com.medislot.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/** Creates the indices (with mappings) on demand, so Elasticsearch being down at startup is not fatal. */
@Component
@ConditionalOnSearchEnabled
public class SearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexManager.class);

    private final ElasticsearchOperations operations;
    private volatile boolean ready;

    public SearchIndexManager(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        try {
            ensureIndices();
        } catch (RuntimeException e) {
            log.warn("Elasticsearch not reachable at startup; indices will be created on first use");
        }
    }

    public void ensureIndices() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (!ready) {
                create(DoctorDocument.class);
                create(PatientDocument.class);
                ready = true;
            }
        }
    }

    /** Drops and recreates both indices (used by reindex). */
    public synchronized void recreateIndices() {
        ready = false;
        operations.indexOps(DoctorDocument.class).delete();
        operations.indexOps(PatientDocument.class).delete();
        ensureIndices();
    }

    private void create(Class<?> type) {
        IndexOperations ops = operations.indexOps(type);
        if (!ops.exists()) {
            ops.createWithMapping();
        }
    }
}
