package ai.skew.openai;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SKEW SDK Tests
 */
class SkewWrapperTest {

    @Test
    void testGenerateRequestId() {
        String id1 = TelemetryClient.generateRequestId();
        String id2 = TelemetryClient.generateRequestId();
        
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("req_"));
    }

    @Test
    void testHashPrompt() {
        String hash1 = TelemetryClient.hashPrompt("Hello, world!");
        String hash2 = TelemetryClient.hashPrompt("Hello, world!");
        String hash3 = TelemetryClient.hashPrompt("Different prompt");
        
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertEquals(16, hash1.length());
    }

    @Test
    void testCalculateCostKnownModel() {
        double cost = TelemetryClient.calculateCost("gpt-4o", 1000, 500);
        
        // gpt-4o: $2.5/1M input, $10/1M output
        double expected = (1000.0 / 1_000_000) * 2.5 + (500.0 / 1_000_000) * 10;
        assertEquals(expected, cost, 0.0000001);
    }

    @Test
    void testCalculateCostUnknownModel() {
        double cost = TelemetryClient.calculateCost("unknown-model", 1000, 500);
        
        assertTrue(cost > 0);
    }

    @Test
    void testSkewConfigBuilder() {
        SkewConfig config = SkewConfig.builder("sk_test_123")
                .orgId("org_test")
                .projectId("project_test")
                .build();
        
        assertEquals("sk_test_123", config.getApiKey());
        assertEquals("org_test", config.getOrgId());
        assertEquals("project_test", config.getProjectId());
        assertNotNull(config.getTelemetry());
        assertNotNull(config.getProxy());
    }

    @Test
    void testTelemetryConfigDefaults() {
        SkewConfig.TelemetryConfig config = SkewConfig.TelemetryConfig.defaults();
        
        assertTrue(config.isEnabled());
        assertFalse(config.isIncludePrompts());
        assertEquals(1.0, config.getSampleRate());
        assertEquals(10, config.getBatchSize());
        assertEquals(5000, config.getFlushIntervalMs());
    }

    @Test
    void testProxyConfigDefaults() {
        SkewConfig.ProxyConfig config = SkewConfig.ProxyConfig.defaults();
        
        assertFalse(config.isEnabled());
        assertTrue(config.isFailOpen());
        assertEquals("https://api.skew.ai/v1/openai", config.getBaseUrl());
        assertEquals(30000, config.getTimeoutMs());
    }

    @Test
    void testTelemetryPayloadBuilder() {
        TelemetryClient.TelemetryPayload payload = TelemetryClient.TelemetryPayload.builder()
                .requestId("req_123")
                .orgId("org_test")
                .endpoint("chat.completions")
                .model("gpt-4o")
                .timestampStart("2024-01-01T00:00:00Z")
                .timestampEnd("2024-01-01T00:00:01Z")
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .costEstimateUsd(0.001)
                .latencyMs(1000)
                .build();
        
        assertNotNull(payload);
        var map = payload.toMap();
        assertEquals("req_123", ((java.util.Map<?, ?>) map.get("request")).get("requestId"));
    }

    @Test
    void testTelemetryClientPauseResume() {
        SkewConfig.TelemetryConfig config = SkewConfig.TelemetryConfig.builder()
                .enabled(true)
                .build();
        
        TelemetryClient client = new TelemetryClient("sk_test", config);
        
        // Should not throw
        assertDoesNotThrow(() -> client.pause());
        assertDoesNotThrow(() -> client.resume());
        
        client.shutdown();
    }

    @Test
    void testTelemetryClientSampling() {
        SkewConfig.TelemetryConfig config = SkewConfig.TelemetryConfig.builder()
                .enabled(true)
                .sampleRate(0.0) // 0% sampling
                .build();
        
        TelemetryClient client = new TelemetryClient("sk_test", config);
        
        TelemetryClient.TelemetryPayload payload = TelemetryClient.TelemetryPayload.builder()
                .requestId("req_123")
                .orgId("org_test")
                .endpoint("chat.completions")
                .model("gpt-4o")
                .timestampStart("2024-01-01T00:00:00Z")
                .timestampEnd("2024-01-01T00:00:01Z")
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .costEstimateUsd(0.001)
                .latencyMs(1000)
                .build();
        
        // With 0% sampling, nothing should be submitted
        assertDoesNotThrow(() -> client.submit(payload));
        
        client.shutdown();
    }

    @Test
    void testProxyModeConfiguration() {
        SkewConfig configNoProxy = SkewConfig.builder("sk_test_123")
                .proxy(SkewConfig.ProxyConfig.builder()
                        .enabled(false)
                        .build())
                .build();
        
        assertFalse(configNoProxy.getProxy().isEnabled());
        
        SkewConfig configWithProxy = SkewConfig.builder("sk_test_123")
                .proxy(SkewConfig.ProxyConfig.builder()
                        .enabled(true)
                        .baseUrl("https://custom.proxy.ai")
                        .build())
                .build();
        
        assertTrue(configWithProxy.getProxy().isEnabled());
        assertEquals("https://custom.proxy.ai", configWithProxy.getProxy().getBaseUrl());
    }

    // Interface for mock OpenAI service
    interface MockOpenAIService {
        Object createChatCompletion(Object request);
    }

    @Test
    void testWrapperBasicFunctionality() {
        // Create a mock service
        MockOpenAIService mockService = mock(MockOpenAIService.class);
        when(mockService.createChatCompletion(any())).thenReturn(new Object());
        
        SkewConfig config = SkewConfig.builder("sk_test_123")
                .orgId("org_test")
                .build();
        
        MockOpenAIService wrappedService = SkewWrapper.wrap(mockService, config);
        
        // Call should work
        assertDoesNotThrow(() -> wrappedService.createChatCompletion(new Object()));
        
        // Original method should have been called
        verify(mockService, times(1)).createChatCompletion(any());
    }

    @Test
    void testWrapperErrorPassthrough() {
        MockOpenAIService mockService = mock(MockOpenAIService.class);
        when(mockService.createChatCompletion(any())).thenThrow(new RuntimeException("Rate limit exceeded"));
        
        SkewConfig config = SkewConfig.builder("sk_test_123").build();
        MockOpenAIService wrappedService = SkewWrapper.wrap(mockService, config);
        
        // Error should pass through
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            wrappedService.createChatCompletion(new Object());
        });
        
        assertEquals("Rate limit exceeded", exception.getMessage());
    }
}
