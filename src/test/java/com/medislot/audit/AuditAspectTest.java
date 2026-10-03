package com.medislot.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medislot.security.AuthenticatedUser;
import com.medislot.user.Role;

class AuditAspectTest {

    static class Target {
        @Audited(action = "READ_PATIENT", resource = "PATIENT", idArg = 1)
        public String read(AuthenticatedUser caller, Long id) {
            return "patient-" + id;
        }

        @Audited(action = "BOOM", resource = "PATIENT", idArg = 1)
        public String fail(AuthenticatedUser caller, Long id) {
            throw new IllegalStateException("business failure");
        }

        public String untouched() {
            return "ok";
        }
    }

    private final AuditLogService audit = mock(AuditLogService.class);
    private Target proxy;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new AuditAspect(audit));
        proxy = factory.getProxy();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, Role.DOCTOR), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulCallRecordsActorActionAndResourceId() {
        assertThat(proxy.read(new AuthenticatedUser(7L, Role.DOCTOR), 42L)).isEqualTo("patient-42");

        verify(audit).record(7L, Role.DOCTOR, "READ_PATIENT", "PATIENT", 42L);
    }

    @Test
    void failedBusinessCallIsNotAuditedAndExceptionPropagates() {
        assertThatThrownBy(() -> proxy.fail(new AuthenticatedUser(7L, Role.DOCTOR), 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(audit, never()).record(anyLong(), any(), anyString(), anyString(), any());
    }

    @Test
    void auditWriteFailureDoesNotBreakTheBusinessCall() {
        doThrow(new RuntimeException("db down")).when(audit).record(anyLong(), any(), anyString(), anyString(), any());

        assertThat(proxy.read(new AuthenticatedUser(7L, Role.DOCTOR), 5L)).isEqualTo("patient-5");
    }

    @Test
    void unannotatedMethodsAreNotAudited() {
        proxy.untouched();

        verify(audit, never()).record(anyLong(), any(), anyString(), anyString(), any());
    }
}
