package io.flowcore.security.authz;

import io.flowcore.security.audit.AuditLogEntry;
import io.flowcore.security.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAuthorizationServiceTest {

    private PolicyEvaluator policyEvaluator;
    private AuditLogService auditLogService;
    private DefaultAuthorizationService service;

    @BeforeEach
    void setUp() {
        policyEvaluator = mock(PolicyEvaluator.class);
        auditLogService = mock(AuditLogService.class);
        service = new DefaultAuthorizationService(policyEvaluator, auditLogService);
    }

    private AuthorizationRequest buildRequest(String action, List<String> roles) {
        return new AuthorizationRequest(
                roles,
                "tenant-1",
                "user-123",
                action,
                new ObjectAttributes("workflow", "wf-001", "tenant-1", Map.of()),
                Map.of("traceId", "trace-1", "spanId", "span-1",
                        "requestIp", "10.0.0.1", "userAgent", "TestAgent/1.0")
        );
    }

    @Nested
    @DisplayName("Policy evaluation")
    class PolicyEvaluationTests {

        @Test
        @DisplayName("should return ALLOW when evaluator allows")
        void shouldReturnAllowWhenEvaluatorAllows() {
            AuthorizationDecision allowDecision = AuthorizationDecision.allow("Policy match");
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(allowDecision);

            AuthorizationRequest request = buildRequest(Actions.WORKFLOW_VIEW, List.of(Roles.VIEWER));
            AuthorizationDecision result = service.evaluate(request);

            assertTrue(result.allowed());
            assertEquals("Policy match", result.reason());
        }

        @Test
        @DisplayName("should return DENY when evaluator denies")
        void shouldReturnDenyWhenEvaluatorDenies() {
            AuthorizationDecision denyDecision = AuthorizationDecision.deny("No match");
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(denyDecision);

            AuthorizationRequest request = buildRequest(Actions.WORKFLOW_START, List.of(Roles.VIEWER));
            AuthorizationDecision result = service.evaluate(request);

            assertFalse(result.allowed());
            assertEquals("No match", result.reason());
        }
    }

    @Nested
    @DisplayName("Audit logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("should record audit entry for ALLOW decision")
        void shouldRecordAuditForAllow() {
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(AuthorizationDecision.allow("OK"));

            AuthorizationRequest request = buildRequest(Actions.WORKFLOW_VIEW, List.of(Roles.VIEWER));
            service.evaluate(request);

            verify(auditLogService).recordAudit(any(AuditLogEntry.class));
        }

        @Test
        @DisplayName("should record audit entry for DENY decision")
        void shouldRecordAuditForDeny() {
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(AuthorizationDecision.deny("Forbidden"));

            AuthorizationRequest request = buildRequest(Actions.WORKFLOW_START, List.of(Roles.VIEWER));
            service.evaluate(request);

            verify(auditLogService).recordAudit(any(AuditLogEntry.class));
        }

        @Test
        @DisplayName("should not fail when audit logging throws exception")
        void shouldNotFailWhenAuditThrows() {
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(AuthorizationDecision.allow("OK"));
            doThrow(new RuntimeException("DB error")).when(auditLogService).recordAudit(any(AuditLogEntry.class));

            AuthorizationRequest request = buildRequest(Actions.WORKFLOW_VIEW, List.of(Roles.VIEWER));
            AuthorizationDecision result = service.evaluate(request);

            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("should handle request with empty environment")
        void shouldHandleEmptyEnvironment() {
            when(policyEvaluator.evaluate(any(), any(), any(), any()))
                    .thenReturn(AuthorizationDecision.allow("OK"));

            AuthorizationRequest request = new AuthorizationRequest(
                    List.of(Roles.ADMIN),
                    "tenant-1",
                    "user-123",
                    Actions.WORKFLOW_VIEW,
                    new ObjectAttributes("workflow", "wf-001", "tenant-1", Map.of()),
                    Map.of()
            );

            AuthorizationDecision result = service.evaluate(request);
            assertTrue(result.allowed());
            verify(auditLogService).recordAudit(any(AuditLogEntry.class));
        }
    }
}
