package io.flowcore.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.InboxService;
import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.dto.InboxAcceptResult;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.WorkflowInstance;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaCommandConsumerTest {

    @Mock
    private InboxService inboxService;

    @Mock
    private WorkflowEngine workflowEngine;

    private ObjectMapper objectMapper;

    private KafkaCommandConsumer consumer;

    @Captor
    private ArgumentCaptor<StartWorkflowCommand> startCaptor;

    @Captor
    private ArgumentCaptor<SignalWorkflowCommand> signalCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new KafkaCommandConsumer(inboxService, workflowEngine, objectMapper);
    }

    private ConsumerRecord<String, byte[]> buildRecord(
            String topic, String commandType, String messageId, byte[] payload) {
        RecordHeaders headers = new RecordHeaders();
        if (commandType != null) {
            headers.add(new RecordHeader("x-command-type",
                    commandType.getBytes(StandardCharsets.UTF_8)));
        }
        if (messageId != null) {
            headers.add(new RecordHeader("x-message-id",
                    messageId.getBytes(StandardCharsets.UTF_8)));
        }
        return new ConsumerRecord<>(topic, 0, 0,
                System.currentTimeMillis(), TimestampType.CREATE_TIME,
                0L, 0, 0, "key", payload, headers);
    }

    private void mockAccept() {
        when(inboxService.tryAccept(anyString(), anyString(), eq("flowcore-command-consumer")))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));
    }

    private void mockReject() {
        when(inboxService.tryAccept(anyString(), anyString(), eq("flowcore-command-consumer")))
                .thenReturn(new InboxAcceptResult(false, null));
    }

    private WorkflowInstance buildInstance() {
        return new WorkflowInstance(
                UUID.randomUUID(), "OrderWorkflow", "order-1",
                "RUNNING", "Start", 0, "{}",
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("START_WORKFLOW command is delegated to WorkflowEngine")
    void startWorkflowCommand() throws Exception {
        mockAccept();
        when(workflowEngine.startWorkflow(any())).thenReturn(buildInstance());

        String payload = objectMapper.writeValueAsString(Map.of(
                "workflowType", "OrderWorkflow",
                "businessKey", "order-1",
                "contextData", Map.of("amount", 100),
                "idempotencyKey", "idem-1"
        ));

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.start", "START_WORKFLOW", "msg-1",
                payload.getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine).startWorkflow(startCaptor.capture());
        StartWorkflowCommand cmd = startCaptor.getValue();
        assertEquals("OrderWorkflow", cmd.workflowType());
        assertEquals("order-1", cmd.businessKey());
        assertEquals("idem-1", cmd.idempotencyKey());
    }

    @Test
    @DisplayName("SIGNAL command is delegated to WorkflowEngine")
    void signalCommand() throws Exception {
        mockAccept();
        UUID instanceId = UUID.randomUUID();

        String payload = objectMapper.writeValueAsString(Map.of(
                "instanceId", instanceId.toString(),
                "eventType", "PaymentReceived",
                "payload", Map.of("paymentId", "pay-1"),
                "idempotencyKey", "idem-2"
        ));

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.signal", "SIGNAL", "msg-2",
                payload.getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine).signal(signalCaptor.capture());
        SignalWorkflowCommand cmd = signalCaptor.getValue();
        assertEquals(instanceId, cmd.instanceId());
        assertEquals("PaymentReceived", cmd.eventType());
        assertEquals("idem-2", cmd.idempotencyKey());
    }

    @Test
    @DisplayName("Duplicate message is ignored via InboxService")
    void duplicateMessageIgnored() {
        mockReject();

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.start", "START_WORKFLOW", "msg-dup",
                "{}".getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine, never()).startWorkflow(any());
        verify(workflowEngine, never()).signal(any());
    }

    @Test
    @DisplayName("Unknown command type logs warning and does not throw")
    void unknownCommandType() {
        mockAccept();

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.unknown", "UNKNOWN_CMD", "msg-3",
                "{}".getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine, never()).startWorkflow(any());
        verify(workflowEngine, never()).signal(any());
    }

    @Test
    @DisplayName("Missing message-id header uses topic-partition-offset fallback")
    void missingMessageIdUsesFallback() throws Exception {
        mockAccept();
        when(workflowEngine.startWorkflow(any())).thenReturn(buildInstance());

        String payload = objectMapper.writeValueAsString(Map.of(
                "workflowType", "TestWF"));

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.start", "START_WORKFLOW", null,
                payload.getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(inboxService).tryAccept(
                eq("flowcore.commands.start"),
                eq("flowcore.commands.start-0-0"),
                eq("flowcore-command-consumer"));
    }

    @Test
    @DisplayName("Invalid JSON payload in START_WORKFLOW logs error gracefully")
    void invalidJsonStartWorkflow() {
        mockAccept();

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.start", "START_WORKFLOW", "msg-4",
                "not-json".getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine, never()).startWorkflow(any());
    }

    @Test
    @DisplayName("Invalid JSON payload in SIGNAL logs error gracefully")
    void invalidJsonSignal() {
        mockAccept();

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.signal", "SIGNAL", "msg-5",
                "broken{json".getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine, never()).signal(any());
    }

    @Test
    @DisplayName("Invalid instanceId in SIGNAL logs error gracefully")
    void invalidInstanceIdSignal() {
        mockAccept();

        String payload = """
                {"instanceId":"not-a-uuid","eventType":"Test"}
                """;

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.signal", "SIGNAL", "msg-6",
                payload.getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine, never()).signal(any());
    }

    @Test
    @DisplayName("START_WORKFLOW with null contextData defaults to empty map")
    void startWorkflowNullContextData() throws Exception {
        mockAccept();
        when(workflowEngine.startWorkflow(any())).thenReturn(buildInstance());

        String payload = objectMapper.writeValueAsString(Map.of(
                "workflowType", "SimpleWF"));

        ConsumerRecord<String, byte[]> record = buildRecord(
                "flowcore.commands.start", "START_WORKFLOW", "msg-7",
                payload.getBytes(StandardCharsets.UTF_8));

        consumer.consume(record);

        verify(workflowEngine).startWorkflow(startCaptor.capture());
        StartWorkflowCommand cmd = startCaptor.getValue();
        assertEquals(Map.of(), cmd.contextData());
        assertNull(cmd.businessKey());
        assertNull(cmd.idempotencyKey());
    }
}
