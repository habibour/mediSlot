package com.medislot.audit.dto;

import java.time.Instant;

import com.medislot.audit.AuditLog;
import com.medislot.user.Role;

public record AuditLogResponse(Long id, Long actorUserId, Role actorRole, String action, String resourceType,
        Long resourceId, Instant createdAt) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getActorRole(), a.getAction(),
                a.getResourceType(), a.getResourceId(), a.getCreatedAt());
    }
}
