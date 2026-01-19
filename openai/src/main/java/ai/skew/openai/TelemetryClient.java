package ai.langmesh.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * langmesh Telemetry Client
 * 
 * Async, non-blocking telemetry submission
 */
public class TelemetryClient {
    private static final String SDK_VERSION = "1.0.0";
    private static final String SDK_LANGUAGE = "java";
    
    private final String apiKey;
    private final langmeshConfig.TelemetryConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<TelemetryPayload> buffer;
    private final ScheduledExecutorService scheduler;
    private volatile boolean paused = false;

    public TelemetryClient(String apiKey, langmeshConfig.TelemetryConfig config) {
        this.apiKey = apiKey;
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.buffer = Collections.synchronizedList(new ArrayList<>());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "langmesh-telemetry");
            t.setDaemon(true);
            return t;
        });
        
        if (config.isEnabled()) {
            startFlushTimer();
        }
    }

    /**
     * Submit telemetry - never blocks, never throws
     */
    public void submit(TelemetryPayload payload) {
        if (!config.isEnabled() || paused) {
            return;
        }
        
        // Apply sampling
        if (Math.random() > config.getSampleRate()) {
            return;
        }
        
        buffer.add(payload);
        
        if (buffer.size() >= config.getBatchSize()) {
            flushAsync();
        }
    }

    /**
     * Flush buffered telemetry synchronously
     */
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        
        List<TelemetryPayload> batch;
        synchronized (buffer) {
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        
        try {
            sendBatch(batch);
        } catch (Exception e) {
            // Silent drop - telemetry must never affect user
        }
    }

    /**
     * Flush in background thread
     */
    public void flushAsync() {
        scheduler.submit(this::flush);
    }

    private void sendBatch(List<TelemetryPayload> batch) throws IOException {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();
        
        for (TelemetryPayload payload : batch) {
            events.add(payload.toMap());
        }
        
        body.put("events", events);
        
        String json = objectMapper.writeValueAsString(body);
        
        Request request = new Request.Builder()
                .url(config.getEndpoint())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("X-langmesh-SDK-Version", SDK_VERSION)
                .addHeader("X-langmesh-SDK-Language", SDK_LANGUAGE)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            // Just consume the response
        }
    }

    private void startFlushTimer() {
        scheduler.scheduleAtFixedRate(
                this::flush,
                config.getFlushIntervalMs(),
                config.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void shutdown() {
        flush();
        scheduler.shutdown();
    }

    // Helper methods
    
    public static String generateRequestId() {
        return "req_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String hashPrompt(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(prompt.hashCode());
        }
    }

    public static double calculateCost(String model, int promptTokens, int completionTokens) {
        Map<String, double[]> pricing = new HashMap<>();
        pricing.put("gpt-4o", new double[]{2.5, 10.0});
        pricing.put("gpt-4o-mini", new double[]{0.15, 0.6});
        pricing.put("gpt-4-turbo", new double[]{10.0, 30.0});
        pricing.put("gpt-4", new double[]{30.0, 60.0});
        pricing.put("gpt-3.5-turbo", new double[]{0.5, 1.5});
        
        double[] prices = pricing.get(model);
        if (prices == null) {
            return (promptTokens + completionTokens) * 0.00001;
        }
        
        return (promptTokens / 1_000_000.0) * prices[0] + (completionTokens / 1_000_000.0) * prices[1];
    }

    /**
     * Telemetry payload
     */
    public static class TelemetryPayload {
        private final String requestId;
        private final String orgId;
        private final String projectId;
        private final String endpoint;
        private final String model;
        private final Integer maxTokens;
        private final Double temperature;
        private final String timestampStart;
        private final String timestampEnd;
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;
        private final double costEstimateUsd;
        private final long latencyMs;
        private final String errorClass;
        private final String errorMessage;
        private final String promptHash;

        public TelemetryPayload(Builder builder) {
            this.requestId = builder.requestId;
            this.orgId = builder.orgId;
            this.projectId = builder.projectId;
            this.endpoint = builder.endpoint;
            this.model = builder.model;
            this.maxTokens = builder.maxTokens;
            this.temperature = builder.temperature;
            this.timestampStart = builder.timestampStart;
            this.timestampEnd = builder.timestampEnd;
            this.promptTokens = builder.promptTokens;
            this.completionTokens = builder.completionTokens;
            this.totalTokens = builder.totalTokens;
            this.costEstimateUsd = builder.costEstimateUsd;
            this.latencyMs = builder.latencyMs;
            this.errorClass = builder.errorClass;
            this.errorMessage = builder.errorMessage;
            this.promptHash = builder.promptHash;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            
            Map<String, Object> request = new HashMap<>();
            request.put("requestId", requestId);
            request.put("orgId", orgId);
            request.put("projectId", projectId);
            request.put("endpoint", endpoint);
            request.put("model", model);
            request.put("maxTokens", maxTokens);
            request.put("temperature", temperature);
            request.put("timestampStart", timestampStart);
            
            Map<String, Object> response = new HashMap<>();
            response.put("timestampEnd", timestampEnd);
            Map<String, Object> tokenUsage = new HashMap<>();
            tokenUsage.put("promptTokens", promptTokens);
            tokenUsage.put("completionTokens", completionTokens);
            tokenUsage.put("totalTokens", totalTokens);
            response.put("tokenUsage", tokenUsage);
            response.put("costEstimateUsd", costEstimateUsd);
            response.put("latencyMs", latencyMs);
            response.put("errorClass", errorClass);
            response.put("errorMessage", errorMessage);
            
            Map<String, Object> context = new HashMap<>();
            context.put("sdkLanguage", SDK_LANGUAGE);
            context.put("sdkVersion", SDK_VERSION);
            context.put("openaiClientVersion", "unknown");
            context.put("promptHash", promptHash);
            
            map.put("request", request);
            map.put("response", response);
            map.put("context", context);
            
            return map;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String requestId;
            private String orgId;
            private String projectId;
            private String endpoint;
            private String model;
            private Integer maxTokens;
            private Double temperature;
            private String timestampStart;
            private String timestampEnd;
            private int promptTokens;
            private int completionTokens;
            private int totalTokens;
            private double costEstimateUsd;
            private long latencyMs;
            private String errorClass;
            private String errorMessage;
            private String promptHash;

            public Builder requestId(String requestId) { this.requestId = requestId; return this; }
            public Builder orgId(String orgId) { this.orgId = orgId; return this; }
            public Builder projectId(String projectId) { this.projectId = projectId; return this; }
            public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
            public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
            public Builder timestampStart(String timestampStart) { this.timestampStart = timestampStart; return this; }
            public Builder timestampEnd(String timestampEnd) { this.timestampEnd = timestampEnd; return this; }
            public Builder promptTokens(int promptTokens) { this.promptTokens = promptTokens; return this; }
            public Builder completionTokens(int completionTokens) { this.completionTokens = completionTokens; return this; }
            public Builder totalTokens(int totalTokens) { this.totalTokens = totalTokens; return this; }
            public Builder costEstimateUsd(double costEstimateUsd) { this.costEstimateUsd = costEstimateUsd; return this; }
            public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }
            public Builder errorClass(String errorClass) { this.errorClass = errorClass; return this; }
            public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
            public Builder promptHash(String promptHash) { this.promptHash = promptHash; return this; }

            public TelemetryPayload build() {
                return new TelemetryPayload(this);
            }
        }
    }
}
