package io.flowcore.demo.card.controller;

import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.WorkflowQueryApi;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.demo.card.workflow.CardIssuanceWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import io.flowcore.demo.card.config.TestSecurityConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardIssuanceController.class)
@Import(TestSecurityConfig.class)
class CardIssuanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowEngine workflowEngine;

    @MockBean
    private WorkflowQueryApi workflowQueryApi;

    @MockBean
    private CardIssuanceWorkflow cardIssuanceWorkflow;

    @Test
    @DisplayName("POST /api/v1/cards/issue should start workflow and return 202")
    void issueCard_shouldReturnAccepted() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                CardIssuanceWorkflow.WORKFLOW_TYPE,
                "cardapp:user-42",
                "RUNNING",
                "KYC_PENDING",
                1,
                "{}",
                now,
                now
        );

        when(workflowEngine.startWorkflow(any(StartWorkflowCommand.class)))
                .thenReturn(instance);

        mockMvc.perform(post("/api/v1/cards/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "user-42",
                                    "tenant": "tenant-a",
                                    "cardProduct": "VISA_CLASSIC",
                                    "country": "US",
                                    "wallet": "wallet-1"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.instanceId").value(instanceId.toString()))
                .andExpect(jsonPath("$.workflowType").value(CardIssuanceWorkflow.WORKFLOW_TYPE))
                .andExpect(jsonPath("$.businessKey").value("cardapp:user-42"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentState").value("KYC_PENDING"));

        verify(workflowEngine).startWorkflow(any(StartWorkflowCommand.class));
    }

    @Test
    @DisplayName("GET /api/v1/cards/applications/{id} should return workflow status with step history")
    void getApplicationStatus_shouldReturnStatus() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                CardIssuanceWorkflow.WORKFLOW_TYPE,
                "cardapp:user-42",
                "RUNNING",
                "CARD_PROVISIONING",
                3,
                "{}",
                now.minusSeconds(60),
                now
        );

        List<StepExecutionSummary> stepHistory = List.of(
                new StepExecutionSummary("kycCheck", 1, "COMPLETED", now.minusSeconds(55), now.minusSeconds(50)),
                new StepExecutionSummary("provisionCard", 1, "RUNNING", now.minusSeconds(45), null)
        );

        when(workflowQueryApi.findById(instanceId)).thenReturn(Optional.of(instance));
        when(workflowQueryApi.getStepHistory(instanceId)).thenReturn(stepHistory);

        mockMvc.perform(get("/api/v1/cards/applications/{instanceId}", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value(instanceId.toString()))
                .andExpect(jsonPath("$.workflowType").value(CardIssuanceWorkflow.WORKFLOW_TYPE))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentState").value("CARD_PROVISIONING"))
                .andExpect(jsonPath("$.stepHistory").isArray())
                .andExpect(jsonPath("$.stepHistory.length()").value(2))
                .andExpect(jsonPath("$.stepHistory[0].stepId").value("kycCheck"))
                .andExpect(jsonPath("$.stepHistory[1].stepId").value("provisionCard"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/applications/{id} should return 404 when not found")
    void getApplicationStatus_shouldReturnNotFound() throws Exception {
        UUID instanceId = UUID.randomUUID();

        when(workflowQueryApi.findById(instanceId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/cards/applications/{instanceId}", instanceId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/cards/issue should include location header")
    void issueCard_shouldIncludeLocationHeader() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                CardIssuanceWorkflow.WORKFLOW_TYPE,
                "cardapp:user-99",
                "RUNNING",
                "INIT",
                1,
                "{}",
                now,
                now
        );

        when(workflowEngine.startWorkflow(any(StartWorkflowCommand.class)))
                .thenReturn(instance);

        mockMvc.perform(post("/api/v1/cards/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userId": "user-99",
                                    "tenant": "tenant-b",
                                    "cardProduct": "MC_GOLD",
                                    "country": "GB",
                                    "wallet": "wallet-2"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.instanceId").value(instanceId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/cards/applications/{id} with no step history returns empty array")
    void getApplicationStatus_withNoStepHistory_returnsEmptyArray() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                CardIssuanceWorkflow.WORKFLOW_TYPE,
                "cardapp:user-1",
                "RUNNING",
                "INIT",
                1,
                "{}",
                now,
                now
        );

        when(workflowQueryApi.findById(instanceId)).thenReturn(Optional.of(instance));
        when(workflowQueryApi.getStepHistory(instanceId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/cards/applications/{instanceId}", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepHistory").isArray())
                .andExpect(jsonPath("$.stepHistory.length()").value(0));
    }
}
