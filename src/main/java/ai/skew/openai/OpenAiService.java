package ai.skew.openai;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import okhttp3.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * SKEW-wrapped OpenAI client - Drop-in replacement
 * 
 * Usage:
 * <pre>
 * // Before
 * import com.theokanning.openai.service.OpenAiService;
 * 
 * // After
 * import ai.skew.openai.OpenAiService;
 * 
 * OpenAiService service = new OpenAiService(apiKey);
 * // Works exactly the same!
 * </pre>
 */
public class OpenAiService extends com.theokanning.openai.service.OpenAiService {
    private static final String SKEW_API_KEY = System.getenv().getOrDefault("SKEW_API_KEY", "");
    private static final String SKEW_TELEMETRY_ENDPOINT = System.getenv()
            .getOrDefault("SKEW_TELEMETRY_ENDPOINT", "https://api.skew.ai/v1/telemetry");
    private static final boolean SKEW_PROXY_ENABLED = "true"
            .equalsIgnoreCase(System.getenv().getOrDefault("SKEW_PROXY_ENABLED", "false"));
    private static final String SKEW_BASE_URL = System.getenv()
            .getOrDefault("SKEW_BASE_URL", "https://api.skew.ai/v1/openai");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final List<TelemetryEvent> telemetryBuffer;
    private final ScheduledExecutorService scheduler;
    private final boolean telemetryEnabled;

    public OpenAiService(String token) {
        this(token, Duration.ofSeconds(30));
    }

    public OpenAiService(String token, Duration timeout) {
        super(token, timeout);
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
        
        this.gson = new Gson();
        this.telemetryBuffer = new CopyOnWriteArrayList<>();
        this.telemetryEnabled = !SKEW_API_KEY.isEmpty();
        this.scheduler = Executors.newScheduledThreadPool(1);

        if (telemetryEnabled) {
            startTelemetry();
        }
    }

    @Override
    public ChatCompletionResult createChatCompletion(ChatCompletionRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = "req_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            ChatCompletionResult result = super.createChatCompletion(request);
            long endTime = System.currentTimeMillis();

            // Collect telemetry
            if (telemetryEnabled) {
                recordTelemetry(new TelemetryEvent(
                    requestId,
                    startTime,
                    endTime,
                    request.getModel(),
                    "chat.completions",
                    endTime - startTime,
                    result.getUsage().getPromptTokens(),
                    result.getUsage().getCompletionTokens(),
                    result.getUsage().getTotalTokens(),
                    estimateCost(request.getModel(), 
                                result.getUsage().getPromptTokens(),
                                result.getUsage().getCompletionTokens()),
                    "success",
                    null,
                    null
                ));
            }

            return result;
        } catch (Exception error) {
            long endTime = System.currentTimeMillis();

            // Record error telemetry
            if (telemetryEnabled) {
                recordTelemetry(new TelemetryEvent(
                    requestId,
                    startTime,
                    endTime,
                    request.getModel(),
                    "chat.completions",
                    endTime - startTime,
                    0,
                    0,
                    0,
                    0.0,
                    "error",
                    error.getClass().getSimpleName(),
                    error.getMessage()
                ));
            }

            throw error;
        }
    }

    private void recordTelemetry(TelemetryEvent event) {
        telemetryBuffer.add(event);

        if (telemetryBuffer.size() >= 10) {
            flushTelemetry();
        }
    }

    private void flushTelemetry() {
        if (telemetryBuffer.isEmpty()) {
            return;
        }

        List<TelemetryEvent> batch = new ArrayList<>(telemetryBuffer);
        telemetryBuffer.clear();

        CompletableFuture.runAsync(() -> {
            try {
                String json = gson.toJson(Map.of("events", batch));
                
                Request request = new Request.Builder()
                        .url(SKEW_TELEMETRY_ENDPOINT)
                        .post(RequestBody.create(json, MediaType.parse("application/json")))
                        .addHeader("Authorization", "Bearer " + SKEW_API_KEY)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    // Silent drop on failure - telemetry must never break user's app
                }
            } catch (IOException e) {
                // Silent drop
            }
        });
    }

    private void startTelemetry() {
        scheduler.scheduleAtFixedRate(this::flushTelemetry, 5, 5, TimeUnit.SECONDS);
    }

    private double estimateCost(String model, int promptTokens, int completionTokens) {
        Map<String, Map<String, Double>> pricing = Map.of(
            "gpt-4o", Map.of("input", 2.5, "output", 10.0),
            "gpt-4o-mini", Map.of("input", 0.15, "output", 0.6),
            "gpt-4-turbo", Map.of("input", 10.0, "output", 30.0),
            "gpt-4", Map.of("input", 30.0, "output", 60.0),
            "gpt-3.5-turbo", Map.of("input", 0.5, "output", 1.5)
        );

        Map<String, Double> modelPricing = pricing.getOrDefault(model, Map.of("input", 0.01, "output", 0.01));
        return (promptTokens / 1_000_000.0) * modelPricing.get("input") +
               (completionTokens / 1_000_000.0) * modelPricing.get("output");
    }

    private static class TelemetryEvent {
        final String request_id;
        final long timestamp_start;
        final long timestamp_end;
        final String model;
        final String endpoint;
        final long latency_ms;
        final int prompt_tokens;
        final int completion_tokens;
        final int total_tokens;
        final double cost_estimate_usd;
        final String status;
        final String error_class;
        final String error_message;

        TelemetryEvent(String requestId, long timestampStart, long timestampEnd, String model,
                      String endpoint, long latencyMs, int promptTokens, int completionTokens,
                      int totalTokens, double costEstimateUsd, String status,
                      String errorClass, String errorMessage) {
            this.request_id = requestId;
            this.timestamp_start = timestampStart;
            this.timestamp_end = timestampEnd;
            this.model = model;
            this.endpoint = endpoint;
            this.latency_ms = latencyMs;
            this.prompt_tokens = promptTokens;
            this.completion_tokens = completionTokens;
            this.total_tokens = totalTokens;
            this.cost_estimate_usd = costEstimateUsd;
            this.status = status;
            this.error_class = errorClass;
            this.error_message = errorMessage;
        }
    }
}
