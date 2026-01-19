package ai.langmesh.openai;

import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;

/**
 * langmesh OpenAI Wrapper
 * 
 * Wraps the OpenAI client with telemetry and optional proxy support.
 * 
 * Invariants:
 * - No behavior changes without explicit configuration
 * - Telemetry is async and non-blocking
 * - SDK errors never break user code
 * - Proxy only activates when explicitly enabled
 */
public class langmeshWrapper {
    
    /**
     * Wrap an OpenAI service with langmesh telemetry and optional proxy
     * 
     * @param service The OpenAI service to wrap
     * @param config langmesh configuration
     * @param <T> The service type
     * @return Wrapped service with telemetry
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(T service, langmeshConfig config) {
        TelemetryClient telemetryClient = new TelemetryClient(
                config.getApiKey(),
                config.getTelemetry()
        );
        
        boolean proxyActive = config.getProxy().isEnabled();
        
        return (T) Proxy.newProxyInstance(
                service.getClass().getClassLoader(),
                service.getClass().getInterfaces(),
                new langmeshInvocationHandler<>(service, config, telemetryClient, proxyActive)
        );
    }

    /**
     * Convenience method to wrap with just API key
     */
    public static <T> T wrap(T service, String apiKey) {
        return wrap(service, langmeshConfig.builder(apiKey).build());
    }

    private static class langmeshInvocationHandler<T> implements InvocationHandler {
        private final T target;
        private final langmeshConfig config;
        private final TelemetryClient telemetryClient;
        private final boolean proxyActive;

        langmeshInvocationHandler(T target, langmeshConfig config, TelemetryClient telemetryClient, boolean proxyActive) {
            this.target = target;
            this.config = config;
            this.telemetryClient = telemetryClient;
            this.proxyActive = proxyActive;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            // Handle wrapper-specific methods
            if ("unwrap".equals(methodName)) {
                return target;
            }
            if ("pauseTelemetry".equals(methodName)) {
                telemetryClient.pause();
                return null;
            }
            if ("resumeTelemetry".equals(methodName)) {
                telemetryClient.resume();
                return null;
            }
            if ("flushTelemetry".equals(methodName)) {
                telemetryClient.flush();
                return null;
            }
            if ("isProxyActive".equals(methodName)) {
                return proxyActive;
            }

            String requestId = TelemetryClient.generateRequestId();
            String timestampStart = Instant.now().toString();
            long startTime = System.currentTimeMillis();

            try {
                Object result = method.invoke(target, args);
                long latencyMs = System.currentTimeMillis() - startTime;
                
                // Send telemetry asynchronously
                sendTelemetryAsync(requestId, timestampStart, methodName, args, result, null, latencyMs);
                
                return result;
            } catch (InvocationTargetException e) {
                long latencyMs = System.currentTimeMillis() - startTime;
                
                // Send error telemetry
                sendTelemetryAsync(requestId, timestampStart, methodName, args, null, e.getCause(), latencyMs);
                
                throw e.getCause();
            }
        }

        private void sendTelemetryAsync(
                String requestId,
                String timestampStart,
                String methodName,
                Object[] args,
                Object result,
                Throwable error,
                long latencyMs
        ) {
            new Thread(() -> {
                try {
                    sendTelemetry(requestId, timestampStart, methodName, args, result, error, latencyMs);
                } catch (Exception e) {
                    // Silent drop
                }
            }, "langmesh-telemetry-send").start();
        }

        private void sendTelemetry(
                String requestId,
                String timestampStart,
                String methodName,
                Object[] args,
                Object result,
                Throwable error,
                long latencyMs
        ) {
            String timestampEnd = Instant.now().toString();
            
            // Try to extract model and usage from request/result
            String model = extractModel(args);
            int[] tokenUsage = extractTokenUsage(result);
            int promptTokens = tokenUsage[0];
            int completionTokens = tokenUsage[1];
            int totalTokens = tokenUsage[2];
            
            TelemetryClient.TelemetryPayload payload = TelemetryClient.TelemetryPayload.builder()
                    .requestId(requestId)
                    .orgId(config.getOrgId() != null ? config.getOrgId() : "")
                    .projectId(config.getProjectId())
                    .endpoint(methodToEndpoint(methodName))
                    .model(model)
                    .timestampStart(timestampStart)
                    .timestampEnd(timestampEnd)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .costEstimateUsd(TelemetryClient.calculateCost(model, promptTokens, completionTokens))
                    .latencyMs(latencyMs)
                    .errorClass(error != null ? error.getClass().getSimpleName() : null)
                    .errorMessage(error != null ? error.getMessage() : null)
                    .build();
            
            telemetryClient.submit(payload);
        }

        private String extractModel(Object[] args) {
            if (args == null || args.length == 0) {
                return "unknown";
            }
            
            try {
                Object request = args[0];
                Method getModel = request.getClass().getMethod("getModel");
                Object model = getModel.invoke(request);
                return model != null ? model.toString() : "unknown";
            } catch (Exception e) {
                return "unknown";
            }
        }

        private int[] extractTokenUsage(Object result) {
            if (result == null) {
                return new int[]{0, 0, 0};
            }
            
            try {
                Method getUsage = result.getClass().getMethod("getUsage");
                Object usage = getUsage.invoke(result);
                
                if (usage == null) {
                    return new int[]{0, 0, 0};
                }
                
                int promptTokens = (int) usage.getClass().getMethod("getPromptTokens").invoke(usage);
                int completionTokens = (int) usage.getClass().getMethod("getCompletionTokens").invoke(usage);
                int totalTokens = (int) usage.getClass().getMethod("getTotalTokens").invoke(usage);
                
                return new int[]{promptTokens, completionTokens, totalTokens};
            } catch (Exception e) {
                return new int[]{0, 0, 0};
            }
        }

        private String methodToEndpoint(String methodName) {
            Map<String, String> mapping = new HashMap<>();
            mapping.put("createChatCompletion", "chat.completions");
            mapping.put("createCompletion", "completions");
            mapping.put("createImage", "images.generate");
            mapping.put("createTranscription", "audio.transcriptions");
            mapping.put("createTranslation", "audio.translations");
            mapping.put("createEmbeddings", "embeddings");
            mapping.put("createModeration", "moderations");
            
            return mapping.getOrDefault(methodName, "unknown");
        }
    }

    /**
     * Interface for wrapped clients
     */
    public interface Wrapped<T> {
        T unwrap();
        void pauseTelemetry();
        void resumeTelemetry();
        void flushTelemetry();
        boolean isProxyActive();
    }
}
