package io.flowcore.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.InboxService;
import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.dto.InboxAcceptResult;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.StartWorkflowCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer that processes incoming commands from the
 * {@code flowcore.commands.#} topic pattern.
 * <p>
 * Each incoming message is deduplicated via the {@link InboxService}
 * before being dispatched to the {@link WorkflowEngine}.
 * Commands are distinguished by the {@code x-command-type} header:
 * <ul>
 *   <li>{@code START_WORKFLOW} - delegates to {@link WorkflowEngine#startWorkflow}</li>
 *   <li>{@code SIGNAL} - delegates to {@link WorkflowEngine#signal}</li>
 * </ul>
 */
@Component
public class KafkaCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaCommandConsumer.class);

    private static final String CMD_START_WORKFLOW = "START_WORKFLOW";
    private static final String CMD_SIGNAL = "SIGNAL";
    private static final String HEADER_COMMAND_TYPE = "x-command-type";
    private static final String HEADER_MESSAGE_ID = "x-message-id";

    private final InboxService inboxService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public KafkaCommandConsumer(InboxService inboxService,
                                WorkflowEngine workflowEngine,
                                ObjectMapper objectMapper) {
        this.inboxService = inboxService;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topicPattern = "flowcore\\.commands\\..*",
            groupId = "${flowcore.kafka.consumer-group:flowcore-consumer}")
    public void consume(ConsumerRecord<String, byte[]> record) {
        String commandType = headerAsString(record, HEADER_COMMAND_TYPE);
        String messageId = headerAsString(record, HEADER_MESSAGE_ID);

        if (messageId == null || messageId.isBlank()) {
            messageId = buildFallbackMessageId(record);
        }

        InboxAcceptResult acceptResult =
                inboxService.tryAccept(record.topic(), messageId, "flowcore-command-consumer");
        if (!acceptResult.accepted()) {
            log.debug("Duplicate command ignored: topic={}, messageId={}", record.topic(), messageId);
            return;
        }

        log.debug("Processing command: type={}, topic={}, messageId={}", commandType, record.topic(), messageId);

        switch (commandType) {
            case CMD_START_WORKFLOW -> handleStartWorkflow(record);
            case CMD_SIGNAL -> handleSignal(record);
            default -> log.warn("Unknown command type: {}, ignoring message on topic {}",
                    commandType, record.topic());
        }
    }

    private void handleStartWorkflow(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String workflowType = root.path("workflowType").asText();
            String businessKey = nullableText(root, "businessKey");
            String idempotencyKey = nullableText(root, "idempotencyKey");
            Map<String, Object> contextData = parseContextData(root);

            StartWorkflowCommand command = new StartWorkflowCommand(
                    workflowType, businessKey, contextData, idempotencyKey);
            workflowEngine.startWorkflow(command);
        } catch (IOException e) {
            log.error("Failed to parse START_WORKFLOW payload from topic {}", record.topic(), e);
        }
    }

    private void handleSignal(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            UUID instanceId = UUID.fromString(root.path("instanceId").asText());
            String eventType = root.path("eventType").asText();
            String idempotencyKey = nullableText(root, "idempotencyKey");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload =
                    objectMapper.treeToValue(root.path("payload"), Map.class);

            SignalWorkflowCommand command = new SignalWorkflowCommand(
                    instanceId, eventType, payload, idempotencyKey);
            workflowEngine.signal(command);
        } catch (IOException e) {
            log.error("Failed to parse SIGNAL payload from topic {}", record.topic(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid instanceId in SIGNAL payload from topic {}", record.topic(), e);
        }
    }

    private String headerAsString(ConsumerRecord<String, byte[]> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildFallbackMessageId(ConsumerRecord<String, byte[]> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContextData(JsonNode root) {
        JsonNode data = root.get("contextData");
        if (data == null || data.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.treeToValue(data, Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }
}
