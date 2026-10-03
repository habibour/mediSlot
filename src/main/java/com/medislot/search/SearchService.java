package com.medislot.search;

import java.util.List;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/** Fuzzy full-text search. Returns ids in relevance order; callers hydrate them through the normal services. */
@Service
@ConditionalOnSearchEnabled
public class SearchService {

    static final int MAX_RESULTS = 50;

    private final ElasticsearchOperations operations;
    private final SearchIndexManager indices;

    public SearchService(ElasticsearchOperations operations, SearchIndexManager indices) {
        this.operations = operations;
        this.indices = indices;
    }

    public List<Long> searchDoctors(String text, String specialty, int size) {
        indices.ensureIndices();
        Query query = Query.of(q -> q.bool(b -> {
            b.must(m -> m.multiMatch(mm -> mm.query(text)
                    .fields("fullName^3", "specialty^2", "bio").fuzziness("AUTO")));
            if (specialty != null && !specialty.isBlank()) {
                b.filter(f -> f.match(m -> m.field("specialty").query(specialty.trim())));
            }
            return b;
        }));
        return ids(query, size, DoctorDocument.class);
    }

    public List<Long> searchPatients(String text, int size) {
        indices.ensureIndices();
        Query query = Query.of(q -> q.match(m -> m.field("fullName").query(text).fuzziness("AUTO")));
        return ids(query, size, PatientDocument.class);
    }

    private <T> List<Long> ids(Query query, int size, Class<T> type) {
        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query)
                .withPageable(PageRequest.of(0, Math.min(Math.max(size, 1), MAX_RESULTS))).build();
        return operations.search(nativeQuery, type).getSearchHits().stream()
                .map(hit -> Long.valueOf(hit.getId())).toList();
    }
}
