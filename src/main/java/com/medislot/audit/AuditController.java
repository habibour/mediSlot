package com.medislot.audit;

import java.time.Instant;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medislot.audit.dto.AuditLogResponse;
import com.medislot.common.dto.PageResponse;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogService service;

    public AuditController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        var pageable = PageResponse.pageable(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        return PageResponse.of(service.search(actorUserId, resourceType, resourceId, from, to, pageable),
                AuditLogResponse::from);
    }
}
