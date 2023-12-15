package io.flowcore.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.runtime.engine.DefaultWorkflowEngine;
import io.flowcore.runtime.query.DefaultWorkflowQueryApi;
import io.flowcore.runtime.registry.DefaultWorkflowRegistry;
import io.flowcore.statemachine.compiler.WorkflowCompiler;
import io.flowcore.statemachine.validation.WorkflowValidator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Flowcore runtime module.
 * Registers the core beans needed for workflow engine operation:
 * validator, compiler, registry, engine, and query API.
 */
@AutoConfiguration
public class RuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkflowValidator workflowValidator() {
        return new WorkflowValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowCompiler workflowCompiler(WorkflowValidator validator) {
        return new WorkflowCompiler(validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultWorkflowRegistry defaultWorkflowRegistry(WorkflowCompiler compiler) {
        return new DefaultWorkflowRegistry(compiler);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultWorkflowEngine defaultWorkflowEngine(
            io.flowcore.runtime.persistence.WorkflowInstanceRepository instanceRepository,
            io.flowcore.runtime.persistence.WorkflowTokenRepository tokenRepository,
            io.flowcore.runtime.persistence.WorkflowStepExecutionRepository stepExecutionRepository,
            DefaultWorkflowRegistry registry,
            ObjectMapper objectMapper) {
        return new DefaultWorkflowEngine(
                instanceRepository, tokenRepository, stepExecutionRepository, registry, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultWorkflowQueryApi defaultWorkflowQueryApi(
            io.flowcore.runtime.persistence.WorkflowInstanceRepository instanceRepository,
            io.flowcore.runtime.persistence.WorkflowStepExecutionRepository stepExecutionRepository) {
        return new DefaultWorkflowQueryApi(instanceRepository, stepExecutionRepository);
    }
}
