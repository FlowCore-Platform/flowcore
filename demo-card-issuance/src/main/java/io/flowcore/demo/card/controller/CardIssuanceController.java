package io.flowcore.demo.card.controller;

import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.WorkflowQueryApi;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.demo.card.workflow.CardIssuanceWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the card issuance demo application.
 *
 * <p>Exposes endpoints to issue new cards and track application status.
 */
@RestController
@RequestMapping("/api/v1")
public class CardIssuanceController {

    private static final Logger log = LoggerFactory.getLogger(CardIssuanceController.class);

    private final WorkflowEngine workflowEngine;
    private final WorkflowQueryApi workflowQueryApi;

    public CardIssuanceController(WorkflowEngine workflowEngine,
                                  WorkflowQueryApi workflowQueryApi) {
        this.workflowEngine = workflowEngine;
        this.workflowQueryApi = workflowQueryApi;
    }

    /**
     * Submit a new card issuance request.
     *
     * @param request the card issuance request body
     * @return 202 Accepted with workflow instance details
     */
    @PostMapping("/cards/issue")
    public ResponseEntity<Map<String, Object>> issueCard(@RequestBody CardIssuanceRequest request) {
        log.info("Received card issuance request for userId={}", request.userId());

        String businessKey = "cardapp:" + request.userId();

        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("userId", request.userId());
        contextData.put("tenant", request.tenant());
        contextData.put("cardProduct", request.cardProduct());
        contextData.put("country", request.country());
        contextData.put("wallet", request.wallet());

        StartWorkflowCommand command = new StartWorkflowCommand(
                CardIssuanceWorkflow.WORKFLOW_TYPE,
                businessKey,
                contextData,
                null
        );

        WorkflowInstance instance = workflowEngine.startWorkflow(command);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("instanceId", instance.id());
        response.put("workflowType", instance.workflowType());
        response.put("businessKey", instance.businessKey());
        response.put("status", instance.status());
        response.put("currentState", instance.currentState());
        response.put("createdAt", instance.createdAt());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/cards/applications/" + instance.id()))
                .body(response);
    }

    /**
     * Retrieve the status of a card issuance application.
     *
     * @param instanceId the workflow instance identifier
     * @return 200 OK with workflow instance status and step history
     */
    @GetMapping("/cards/applications/{instanceId}")
    public ResponseEntity<Map<String, Object>> getApplicationStatus(
            @PathVariable UUID instanceId) {
        log.debug("Querying card application status for instanceId={}", instanceId);

        return workflowQueryApi.findById(instanceId)
                .map(instance -> {
                    List<StepExecutionSummary> stepHistory =
                            workflowQueryApi.getStepHistory(instanceId);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("instanceId", instance.id());
                    response.put("workflowType", instance.workflowType());
                    response.put("businessKey", instance.businessKey());
                    response.put("status", instance.status());
                    response.put("currentState", instance.currentState());
                    response.put("version", instance.version());
                    response.put("createdAt", instance.createdAt());
                    response.put("updatedAt", instance.updatedAt());
                    response.put("stepHistory", stepHistory);

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Request body for card issuance.
     */
    public record CardIssuanceRequest(
            String userId,
            String tenant,
            String cardProduct,
            String country,
            String wallet
    ) {}
}
