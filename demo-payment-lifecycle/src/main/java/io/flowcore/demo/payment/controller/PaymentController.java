package io.flowcore.demo.payment.controller;

import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.WorkflowQueryApi;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.demo.payment.workflow.PaymentWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller exposing payment lifecycle operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/payments - initiate a new payment</li>
 *   <li>POST /api/v1/payments/{id}/capture - signal capture</li>
 *   <li>POST /api/v1/payments/{id}/refund - signal refund request</li>
 *   <li>GET /api/v1/payments/{id} - query payment status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final WorkflowEngine engine;
    private final WorkflowQueryApi queryApi;

    public PaymentController(WorkflowEngine engine, WorkflowQueryApi queryApi) {
        this.engine = engine;
        this.queryApi = queryApi;
    }

    /**
     * Initiate a new payment workflow.
     *
     * @param request payment initiation request body
     * @return 202 Accepted with the created workflow instance
     */
    @PostMapping("/payments")
    public ResponseEntity<WorkflowInstance> createPayment(@RequestBody CreatePaymentRequest request) {
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("merchantId", request.merchantId());
        contextData.put("amountMinor", request.amountMinor());
        contextData.put("currency", request.currency());
        contextData.put("paymentMethod", request.paymentMethod());
        contextData.put("customerId", request.customerId());

        StartWorkflowCommand command = new StartWorkflowCommand(
                PaymentWorkflow.WORKFLOW_TYPE,
                request.idempotencyKey(),
                contextData,
                request.idempotencyKey()
        );

        WorkflowInstance instance = engine.startWorkflow(command);

        URI location = URI.create("/api/v1/payments/" + instance.id());
        return ResponseEntity.accepted().location(location).body(instance);
    }

    /**
     * Signal a capture event for the given payment.
     *
     * @param id workflow instance id
     * @return 200 OK
     */
    @PostMapping("/payments/{id}/capture")
    public ResponseEntity<Void> capturePayment(@PathVariable UUID id) {
        SignalWorkflowCommand command = new SignalWorkflowCommand(
                id,
                PaymentWorkflow.EVENT_CAPTURE,
                Map.of(),
                null
        );
        engine.signal(command);
        return ResponseEntity.ok().build();
    }

    /**
     * Signal a refund request for the given payment.
     *
     * @param id workflow instance id
     * @return 200 OK
     */
    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<Void> refundPayment(@PathVariable UUID id) {
        SignalWorkflowCommand command = new SignalWorkflowCommand(
                id,
                PaymentWorkflow.EVENT_REFUND_REQUEST,
                Map.of(),
                null
        );
        engine.signal(command);
        return ResponseEntity.ok().build();
    }

    /**
     * Get the current status and step history of a payment.
     *
     * @param id workflow instance id
     * @return payment status response with step history
     */
    @GetMapping("/payments/{id}")
    public ResponseEntity<PaymentStatusResponse> getPayment(@PathVariable UUID id) {
        var instanceOpt = queryApi.findById(id);
        if (instanceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkflowInstance instance = instanceOpt.get();
        List<StepExecutionSummary> history = queryApi.getStepHistory(id);

        PaymentStatusResponse response = new PaymentStatusResponse(
                instance.id(),
                instance.workflowType(),
                instance.businessKey(),
                instance.status(),
                instance.currentState(),
                instance.createdAt(),
                instance.updatedAt(),
                history
        );
        return ResponseEntity.ok(response);
    }

    // --- Request / Response records ---

    /**
     * Request body for initiating a new payment.
     */
    public record CreatePaymentRequest(
            String merchantId,
            long amountMinor,
            String currency,
            String paymentMethod,
            String customerId,
            String idempotencyKey
    ) {}

    /**
     * Response body containing payment status and step execution history.
     */
    public record PaymentStatusResponse(
            UUID id,
            String workflowType,
            String businessKey,
            String status,
            String currentState,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            List<StepExecutionSummary> stepHistory
    ) {}
}
