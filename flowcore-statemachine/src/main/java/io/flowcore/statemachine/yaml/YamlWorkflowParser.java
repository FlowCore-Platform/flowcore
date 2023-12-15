package io.flowcore.statemachine.yaml;

import io.flowcore.api.dto.ActivityDef;
import io.flowcore.api.dto.CompensationDef;
import io.flowcore.api.dto.ForkSpec;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimeoutPolicyDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;
import io.flowcore.api.dto.WorkflowDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a YAML workflow definition into a {@link WorkflowDefinition} DTO.
 *
 * <p>Uses SnakeYAML for YAML parsing. Supports both string and stream input.</p>
 *
 * <p>Example YAML format:</p>
 * <pre>{@code
 * workflowType: "demo.card.issuance"
 * version: 1
 * states: [INIT, KYC_PENDING, KYC_APPROVED, ACTIVE, FAILED]
 * initialState: INIT
 * terminalStates: [ACTIVE, FAILED]
 *
 * steps:
 *   - id: kycCheck
 *     kind: asyncActivity
 *     from: INIT
 *     to: KYC_PENDING
 *     activity:
 *       adapter: KycProviderAdapter
 *       operation: verify
 *     retry:
 *       mode: exponential
 *       maxAttempts: 5
 *       baseDelayMs: 200
 *       maxDelayMs: 5000
 *       jitterPct: 20
 *     timeout:
 *       timeoutMs: 10000
 *       onTimeoutState: FAILED
 *     compensation:
 *       kind: asyncActivity
 *       activity:
 *         adapter: KycProviderAdapter
 *         operation: cancelVerification
 *
 * transitions:
 *   - id: kycApproved
 *     from: KYC_PENDING
 *     to: KYC_APPROVED
 *     trigger:
 *       type: event
 *       name: KYC_RESULT
 *     guard: '$.kyc.status == "APPROVED"'
 * }</pre>
 */
public class YamlWorkflowParser {

    private final Yaml yaml;

    /**
     * Creates a new parser with a default SnakeYAML instance.
     */
    public YamlWorkflowParser() {
        this.yaml = new Yaml();
    }

    /**
     * Creates a new parser with a custom SnakeYAML instance.
     *
     * @param yaml the SnakeYAML instance to use
     */
    public YamlWorkflowParser(Yaml yaml) {
        this.yaml = yaml;
    }

    /**
     * Parses a YAML workflow definition from a string.
     *
     * @param yamlString the YAML content
     * @return the parsed WorkflowDefinition
     * @throws IllegalArgumentException if the YAML is invalid or missing required fields
     */
    @SuppressWarnings("unchecked")
    public WorkflowDefinition parse(String yamlString) {
        Map<String, Object> root = yaml.load(yamlString);
        return buildDefinition(root);
    }

    /**
     * Parses a YAML workflow definition from an input stream.
     *
     * @param yamlStream the YAML content stream
     * @return the parsed WorkflowDefinition
     * @throws IllegalArgumentException if the YAML is invalid or missing required fields
     */
    @SuppressWarnings("unchecked")
    public WorkflowDefinition parse(InputStream yamlStream) {
        Map<String, Object> root = yaml.load(yamlStream);
        return buildDefinition(root);
    }

    // -- Internal parsing --------------------------------------------------

    @SuppressWarnings("unchecked")
    private WorkflowDefinition buildDefinition(Map<String, Object> root) {
        if (root == null) {
            throw new IllegalArgumentException("YAML root is null or empty");
        }

        String workflowType = requireString(root, "workflowType");
        Object versionObj = requireObject(root, "version");
        String version = String.valueOf(versionObj);

        Set<String> states = parseStringSet(root, "states");
        String initialState = requireString(root, "initialState");
        Set<String> terminalStates = parseStringSet(root, "terminalStates");

        List<StepDef> steps = parseSteps(root);
        List<TransitionDef> transitions = parseTransitions(root);
        List<ForkSpec> forkJoinSpecs = parseForkJoinSpecs(root);

        return new WorkflowDefinition(
                workflowType,
                version,
                states,
                initialState,
                terminalStates,
                steps,
                transitions,
                forkJoinSpecs.isEmpty() ? null : forkJoinSpecs
        );
    }

    // -- Steps parsing -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<StepDef> parseSteps(Map<String, Object> root) {
        List<Object> rawSteps = (List<Object>) root.get("steps");
        if (rawSteps == null) {
            return List.of();
        }

        List<StepDef> steps = new ArrayList<>();
        for (Object rawStep : rawSteps) {
            Map<String, Object> stepMap = (Map<String, Object>) rawStep;
            steps.add(parseStep(stepMap));
        }
        return steps;
    }

    @SuppressWarnings("unchecked")
    private StepDef parseStep(Map<String, Object> map) {
        String id = requireString(map, "id");
        String kind = requireString(map, "kind");
        String from = requireString(map, "from");
        String to = requireString(map, "to");

        ActivityDef activity = parseActivity((Map<String, Object>) map.get("activity"));
        RetryPolicyDef retryPolicy = parseRetryPolicy((Map<String, Object>) map.get("retry"));
        TimeoutPolicyDef timeoutPolicy = parseTimeoutPolicy((Map<String, Object>) map.get("timeout"));
        CompensationDef compensation = parseCompensation((Map<String, Object>) map.get("compensation"));

        return new StepDef(id, kind, from, to, activity, retryPolicy, timeoutPolicy, compensation);
    }

    @SuppressWarnings("unchecked")
    private ActivityDef parseActivity(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String adapter = requireString(map, "adapter");
        String operation = requireString(map, "operation");
        return new ActivityDef(adapter, operation);
    }

    @SuppressWarnings("unchecked")
    private RetryPolicyDef parseRetryPolicy(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String mode = requireString(map, "mode");
        int maxAttempts = requireInt(map, "maxAttempts");
        long baseDelayMs = requireLong(map, "baseDelayMs");
        long maxDelayMs = requireLong(map, "maxDelayMs");
        double jitterPct = map.containsKey("jitterPct")
                ? ((Number) map.get("jitterPct")).doubleValue() / 100.0
                : 0.0;

        return new RetryPolicyDef(mode, maxAttempts, baseDelayMs, maxDelayMs, jitterPct);
    }

    @SuppressWarnings("unchecked")
    private TimeoutPolicyDef parseTimeoutPolicy(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        long timeoutMs = requireLong(map, "timeoutMs");
        String onTimeoutState = (String) map.get("onTimeoutState");
        return new TimeoutPolicyDef(timeoutMs, onTimeoutState);
    }

    @SuppressWarnings("unchecked")
    private CompensationDef parseCompensation(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String kind = (String) map.get("kind");
        Map<String, Object> activityMap = (Map<String, Object>) map.get("activity");
        ActivityDef activity = parseActivity(activityMap);
        return new CompensationDef(kind, activity);
    }

    // -- Transitions parsing -----------------------------------------------

    @SuppressWarnings("unchecked")
    private List<TransitionDef> parseTransitions(Map<String, Object> root) {
        List<Object> rawTransitions = (List<Object>) root.get("transitions");
        if (rawTransitions == null) {
            return List.of();
        }

        List<TransitionDef> transitions = new ArrayList<>();
        for (Object rawTransition : rawTransitions) {
            Map<String, Object> transitionMap = (Map<String, Object>) rawTransition;
            transitions.add(parseTransition(transitionMap));
        }
        return transitions;
    }

    @SuppressWarnings("unchecked")
    private TransitionDef parseTransition(Map<String, Object> map) {
        String id = requireString(map, "id");
        String from = requireString(map, "from");
        String to = requireString(map, "to");
        TriggerDef trigger = parseTrigger((Map<String, Object>) map.get("trigger"));
        String guard = (String) map.get("guard");

        return new TransitionDef(id, from, to, trigger, guard);
    }

    @SuppressWarnings("unchecked")
    private TriggerDef parseTrigger(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        String type = requireString(map, "type");
        String name = requireString(map, "name");
        return new TriggerDef(type, name);
    }

    // -- Fork/join specs parsing -------------------------------------------

    @SuppressWarnings("unchecked")
    private List<ForkSpec> parseForkJoinSpecs(Map<String, Object> root) {
        List<Object> rawSpecs = (List<Object>) root.get("forkJoinSpecs");
        if (rawSpecs == null) {
            return List.of();
        }

        List<ForkSpec> specs = new ArrayList<>();
        for (Object rawSpec : rawSpecs) {
            Map<String, Object> specMap = (Map<String, Object>) rawSpec;
            specs.add(parseForkSpec(specMap));
        }
        return specs;
    }

    @SuppressWarnings("unchecked")
    private ForkSpec parseForkSpec(Map<String, Object> map) {
        String forkStepId = requireString(map, "forkStepId");
        List<String> branchNames = parseStringList(map, "branchNames");
        String joinStepId = requireString(map, "joinStepId");
        String joinPolicy = (String) map.getOrDefault("joinPolicy", "ALL");

        return new ForkSpec(forkStepId, branchNames, joinStepId, joinPolicy);
    }

    // -- Utility methods ---------------------------------------------------

    private String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required field '" + key + "' is missing");
        }
        return value.toString();
    }

    private Object requireObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required field '" + key + "' is missing");
        }
        return value;
    }

    private int requireInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required field '" + key + "' is missing");
        }
        return ((Number) value).intValue();
    }

    private long requireLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required field '" + key + "' is missing");
        }
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseStringSet(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required field '" + key + "' is missing");
        }
        if (value instanceof List) {
            return new LinkedHashSet<>((List<String>) value);
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a list of strings");
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a list of strings");
    }
}
