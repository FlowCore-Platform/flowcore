package io.flowcore.security.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPolicyEvaluatorTest {

    private final ObjectAttributes defaultAttrs = new ObjectAttributes(
            "workflow", "wf-001", "tenant-1", Map.of()
    );

    @Nested
    @DisplayName("ALLOW rule matching")
    class AllowRuleTests {

        @Test
        @DisplayName("should allow when matching ALLOW rule exists")
        void shouldAllowWhenMatchingAllowRule() {
            PolicyRule allowRule = new PolicyRule(
                    "allow-viewer-view",
                    List.of(Roles.VIEWER),
                    Actions.WORKFLOW_VIEW,
                    null,
                    AuthorizationDecision.allow("Viewer can view workflows")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(allowRule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.VIEWER),
                    defaultAttrs,
                    Actions.WORKFLOW_VIEW,
                    Map.of()
            );

            assertTrue(decision.allowed());
            assertEquals("Viewer can view workflows", decision.reason());
        }

        @Test
        @DisplayName("should allow when multiple roles match any required role")
        void shouldAllowWhenAnyRoleMatches() {
            PolicyRule rule = new PolicyRule(
                    "allow-admin-or-operator",
                    List.of(Roles.ADMIN, Roles.OPERATOR),
                    Actions.WORKFLOW_START,
                    null,
                    AuthorizationDecision.allow("Admin or Operator can start")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.OPERATOR),
                    defaultAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertTrue(decision.allowed());
        }
    }

    @Nested
    @DisplayName("DENY rule matching")
    class DenyRuleTests {

        @Test
        @DisplayName("should deny when matching DENY rule exists")
        void shouldDenyWhenMatchingDenyRule() {
            PolicyRule denyRule = new PolicyRule(
                    "deny-viewer-start",
                    List.of(Roles.VIEWER),
                    Actions.WORKFLOW_START,
                    null,
                    AuthorizationDecision.deny("Viewers cannot start workflows")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(denyRule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.VIEWER),
                    defaultAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertFalse(decision.allowed());
            assertEquals("Viewers cannot start workflows", decision.reason());
        }

        @Test
        @DisplayName("should deny when DENY rule matches even if ALLOW also matches")
        void shouldDenyTakesPrecedenceOverAllow() {
            PolicyRule allowRule = new PolicyRule(
                    "allow-all-start",
                    List.of(Roles.VIEWER, Roles.OPERATOR),
                    Actions.WORKFLOW_START,
                    null,
                    AuthorizationDecision.allow("Can start")
            );
            PolicyRule denyRule = new PolicyRule(
                    "deny-viewer-start",
                    List.of(Roles.VIEWER),
                    Actions.WORKFLOW_START,
                    null,
                    AuthorizationDecision.deny("Viewers cannot start")
            );

            // DENY rule comes after ALLOW but should still cause denial
            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(
                    List.of(allowRule, denyRule)
            );

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.VIEWER),
                    defaultAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertFalse(decision.allowed());
        }
    }

    @Nested
    @DisplayName("Default behavior")
    class DefaultBehaviorTests {

        @Test
        @DisplayName("should deny when no rules match")
        void shouldDenyWhenNoRulesMatch() {
            PolicyRule rule = new PolicyRule(
                    "admin-only",
                    List.of(Roles.ADMIN),
                    Actions.WORKFLOW_START,
                    null,
                    AuthorizationDecision.allow("Admin can start")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.VIEWER),
                    defaultAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertFalse(decision.allowed());
            assertEquals("No matching policy rule found", decision.reason());
        }

        @Test
        @DisplayName("should deny when rules list is empty")
        void shouldDenyWhenRulesEmpty() {
            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of());

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.ADMIN),
                    defaultAttrs,
                    Actions.WORKFLOW_VIEW,
                    Map.of()
            );

            assertFalse(decision.allowed());
        }

        @Test
        @DisplayName("should deny when rules list is null")
        void shouldDenyWhenRulesNull() {
            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(null);

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.ADMIN),
                    defaultAttrs,
                    Actions.WORKFLOW_VIEW,
                    Map.of()
            );

            assertFalse(decision.allowed());
        }
    }

    @Nested
    @DisplayName("Role-based matching")
    class RoleBasedTests {

        @Test
        @DisplayName("should match rule when rule has no required roles")
        void shouldMatchWhenNoRolesRequired() {
            PolicyRule rule = new PolicyRule(
                    "public-view",
                    List.of(),
                    Actions.WORKFLOW_VIEW,
                    null,
                    AuthorizationDecision.allow("Anyone can view")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of("UNKNOWN_ROLE"),
                    defaultAttrs,
                    Actions.WORKFLOW_VIEW,
                    Map.of()
            );

            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("should not match rule when action differs")
        void shouldNotMatchWhenActionDiffers() {
            PolicyRule rule = new PolicyRule(
                    "view-only",
                    List.of(Roles.VIEWER),
                    Actions.WORKFLOW_VIEW,
                    null,
                    AuthorizationDecision.allow("Can view")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.VIEWER),
                    defaultAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertFalse(decision.allowed());
        }

        @Test
        @DisplayName("should match when subject has one of the required roles")
        void shouldMatchWhenSubjectHasOneRole() {
            PolicyRule rule = new PolicyRule(
                    "multi-role",
                    List.of(Roles.ADMIN, Roles.OPERATOR, Roles.VIEWER),
                    Actions.CARD_VIEW,
                    null,
                    AuthorizationDecision.allow("Multiple roles allowed")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of("OTHER_ROLE", Roles.OPERATOR, "THIRD_ROLE"),
                    defaultAttrs,
                    Actions.CARD_VIEW,
                    Map.of()
            );

            assertTrue(decision.allowed());
        }
    }

    @Nested
    @DisplayName("ABAC condition matching")
    class AbacConditionTests {

        @Test
        @DisplayName("should allow when ABAC condition passes")
        void shouldAllowWhenAbacConditionPasses() {
            ObjectAttributes tenantAttrs = new ObjectAttributes(
                    "workflow", "wf-001", "tenant-1", Map.of()
            );

            PolicyRule rule = new PolicyRule(
                    "tenant-match",
                    List.of(Roles.OPERATOR),
                    Actions.WORKFLOW_START,
                    attrs -> "tenant-1".equals(attrs.tenantId()),
                    AuthorizationDecision.allow("Tenant match")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.OPERATOR),
                    tenantAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("should deny when ABAC condition fails")
        void shouldDenyWhenAbacConditionFails() {
            ObjectAttributes otherTenantAttrs = new ObjectAttributes(
                    "workflow", "wf-001", "tenant-2", Map.of()
            );

            PolicyRule rule = new PolicyRule(
                    "tenant-match",
                    List.of(Roles.OPERATOR),
                    Actions.WORKFLOW_START,
                    attrs -> "tenant-1".equals(attrs.tenantId()),
                    AuthorizationDecision.allow("Tenant match")
            );

            DefaultPolicyEvaluator evaluator = new DefaultPolicyEvaluator(List.of(rule));

            AuthorizationDecision decision = evaluator.evaluate(
                    List.of(Roles.OPERATOR),
                    otherTenantAttrs,
                    Actions.WORKFLOW_START,
                    Map.of()
            );

            assertFalse(decision.allowed());
        }
    }
}
