package com.medislot.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.medislot.security.AuthenticatedUser;
import com.medislot.security.CurrentUser;

/**
 * Records successful {@link Audited} calls. Only works when the annotated method is invoked through the Spring
 * proxy (controller → service); calls from within the same class bypass it (self-invocation).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();
        try {
            AuthenticatedUser actor = CurrentUser.get();
            auditLogService.record(actor.userId(), actor.role(), audited.action(), audited.resource(),
                    resourceId(pjp.getArgs(), audited.idArg()));
        } catch (RuntimeException e) {
            // never fail the business call because the trail could not be written; make it loud for operators
            log.error("Audit write failed for action={} resource={}", audited.action(), audited.resource(), e);
        }
        return result;
    }

    private static Long resourceId(Object[] args, int index) {
        return index >= 0 && index < args.length && args[index] instanceof Long id ? id : null;
    }
}
