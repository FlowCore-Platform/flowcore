package io.flowcore.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level configuration properties for the FlowCore platform.
 * <p>
 * All settings are prefixed with {@code flowcore}. Individual modules
 * (security, kafka, observability) can be toggled on or off via boolean
 * flags; nested database settings are grouped under {@code flowcore.db}.
 */
@ConfigurationProperties(prefix = "flowcore")
public class FlowcoreProperties {

    private String applicationName = "flowcore-app";
    private String environment = "development";
    private Database db = new Database();
    private boolean securityEnabled = true;
    private boolean kafkaEnabled = true;
    private boolean observabilityEnabled = true;

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Database getDb() {
        return db;
    }

    public void setDb(Database db) {
        this.db = db;
    }

    public boolean isSecurityEnabled() {
        return securityEnabled;
    }

    public void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public boolean isObservabilityEnabled() {
        return observabilityEnabled;
    }

    public void setObservabilityEnabled(boolean observabilityEnabled) {
        this.observabilityEnabled = observabilityEnabled;
    }

    /**
     * Database connection settings grouped under {@code flowcore.db}.
     */
    public static class Database {

        private String url;
        private String username;
        private String password;
        private String schema = "flowcore";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}
