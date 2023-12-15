package io.flowcore.demo.payment.controller;

import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.WorkflowQueryApi;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.demo.payment.workflow.PaymentWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import io.flowcore.demo.payment.config.TestSecurityConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(TestSecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowEngine engine;

    @MockBean
    private WorkflowQueryApi queryApi;

    @MockBean
    private PaymentWorkflow paymentWorkflow;

    @Test
    @DisplayName("POST /payments returns 202 with workflow instance")
    void createPayment_returnsAccepted() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                PaymentWorkflow.WORKFLOW_TYPE,
                null,
                "RUNNING",
                "INITIATED",
                0,
                null,
                now,
                now
        );
        when(engine.startWorkflow(any())).thenReturn(instance);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "merchant-001",
                                  "amountMinor": 10000,
                                  "currency": "USD",
                                  "paymentMethod": "CARD",
                                  "customerId": "cust-001",
                                  "idempotencyKey": "idem-001"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/payments/" + instanceId))
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.workflowType").value(PaymentWorkflow.WORKFLOW_TYPE))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentState").value("INITIATED"));

        verify(engine).startWorkflow(any());
    }

    @Test
    @DisplayName("POST /payments/{id}/capture signals CAPTURE event")
    void capturePayment_signalsCapture() throws Exception {
        UUID instanceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/{id}/capture", instanceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(engine).signal(any());
    }

    @Test
    @DisplayName("POST /payments/{id}/refund signals REFUND_REQUEST event")
    void refundPayment_signalsRefund() throws Exception {
        UUID instanceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/payments/{id}/refund", instanceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(engine).signal(any());
    }

    @Test
    @DisplayName("GET /payments/{id} returns payment status with step history")
    void getPayment_returnsStatus() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowInstance instance = new WorkflowInstance(
                instanceId,
                PaymentWorkflow.WORKFLOW_TYPE,
                "payment-001",
                "COMPLETED",
                "SETTLED",
                5,
                null,
                now,
                now
        );
        when(queryApi.findById(instanceId)).thenReturn(Optional.of(instance));

        StepExecutionSummary step1 = new StepExecutionSummary(
                "authorizePayment", 1, "COMPLETED", now, now.plusMillis(100)
        );
        StepExecutionSummary step2 = new StepExecutionSummary(
                "capturePayment", 1, "COMPLETED", now.plusMillis(100), now.plusMillis(200)
        );
        when(queryApi.getStepHistory(instanceId)).thenReturn(List.of(step1, step2));

        mockMvc.perform(get("/api/v1/payments/{id}", instanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currentState").value("SETTLED"))
                .andExpect(jsonPath("$.stepHistory").isArray())
                .andExpect(jsonPath("$.stepHistory.length()").value(2))
                .andExpect(jsonPath("$.stepHistory[0].stepId").value("authorizePayment"))
                .andExpect(jsonPath("$.stepHistory[1].stepId").value("capturePayment"));
    }

    @Test
    @DisplayName("GET /payments/{id} returns 404 when not found")
    void getPayment_returns404() throws Exception {
        UUID instanceId = UUID.randomUUID();
        when(queryApi.findById(instanceId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payments/{id}", instanceId))
                .andExpect(status().isNotFound());
    }
}
