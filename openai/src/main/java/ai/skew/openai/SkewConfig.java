package ai.langmesh.openai;

/**
 * langmesh SDK Configuration
 */
public class langmeshConfig {
    private final String apiKey;
    private final String orgId;
    private final String projectId;
    private final TelemetryConfig telemetry;
    private final ProxyConfig proxy;

    private langmeshConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.orgId = builder.orgId;
        this.projectId = builder.projectId;
        this.telemetry = builder.telemetry != null ? builder.telemetry : TelemetryConfig.defaults();
        this.proxy = builder.proxy != null ? builder.proxy : ProxyConfig.defaults();
    }

    public String getApiKey() { return apiKey; }
    public String getOrgId() { return orgId; }
    public String getProjectId() { return projectId; }
    public TelemetryConfig getTelemetry() { return telemetry; }
    public ProxyConfig getProxy() { return proxy; }

    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    public static class Builder {
        private final String apiKey;
        private String orgId;
        private String projectId;
        private TelemetryConfig telemetry;
        private ProxyConfig proxy;

        public Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder telemetry(TelemetryConfig telemetry) {
            this.telemetry = telemetry;
            return this;
        }

        public Builder proxy(ProxyConfig proxy) {
            this.proxy = proxy;
            return this;
        }

        public langmeshConfig build() {
            return new langmeshConfig(this);
        }
    }

    /**
     * Telemetry configuration
     */
    public static class TelemetryConfig {
        private final boolean enabled;
        private final boolean includePrompts;
        private final double sampleRate;
        private final int batchSize;
        private final long flushIntervalMs;
        private final String endpoint;

        private TelemetryConfig(boolean enabled, boolean includePrompts, double sampleRate,
                                int batchSize, long flushIntervalMs, String endpoint) {
            this.enabled = enabled;
            this.includePrompts = includePrompts;
            this.sampleRate = sampleRate;
            this.batchSize = batchSize;
            this.flushIntervalMs = flushIntervalMs;
            this.endpoint = endpoint;
        }

        public static TelemetryConfig defaults() {
            return new TelemetryConfig(true, false, 1.0, 10, 5000, "https://api.langmesh.ai/v1/telemetry");
        }

        public boolean isEnabled() { return enabled; }
        public boolean isIncludePrompts() { return includePrompts; }
        public double getSampleRate() { return sampleRate; }
        public int getBatchSize() { return batchSize; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public String getEndpoint() { return endpoint; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private boolean enabled = true;
            private boolean includePrompts = false;
            private double sampleRate = 1.0;
            private int batchSize = 10;
            private long flushIntervalMs = 5000;
            private String endpoint = "https://api.langmesh.ai/v1/telemetry";

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder includePrompts(boolean includePrompts) { this.includePrompts = includePrompts; return this; }
            public Builder sampleRate(double sampleRate) { this.sampleRate = sampleRate; return this; }
            public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
            public Builder flushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; return this; }
            public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }

            public TelemetryConfig build() {
                return new TelemetryConfig(enabled, includePrompts, sampleRate, batchSize, flushIntervalMs, endpoint);
            }
        }
    }

    /**
     * Proxy configuration
     */
    public static class ProxyConfig {
        private final boolean enabled;
        private final boolean failOpen;
        private final String baseUrl;
        private final long timeoutMs;

        private ProxyConfig(boolean enabled, boolean failOpen, String baseUrl, long timeoutMs) {
            this.enabled = enabled;
            this.failOpen = failOpen;
            this.baseUrl = baseUrl;
            this.timeoutMs = timeoutMs;
        }

        public static ProxyConfig defaults() {
            return new ProxyConfig(false, true, "https://api.langmesh.ai/v1/openai", 30000);
        }

        public boolean isEnabled() { return enabled; }
        public boolean isFailOpen() { return failOpen; }
        public String getBaseUrl() { return baseUrl; }
        public long getTimeoutMs() { return timeoutMs; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private boolean enabled = false;
            private boolean failOpen = true;
            private String baseUrl = "https://api.langmesh.ai/v1/openai";
            private long timeoutMs = 30000;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder failOpen(boolean failOpen) { this.failOpen = failOpen; return this; }
            public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
            public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }

            public ProxyConfig build() {
                return new ProxyConfig(enabled, failOpen, baseUrl, timeoutMs);
            }
        }
    }
}
