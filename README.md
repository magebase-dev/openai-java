# SKEW OpenAI (Java)

Drop-in replacement for the OpenAI Java client with automatic cost optimization and telemetry.

## Installation

### Maven
```xml
<dependency>
    <groupId>ai.skew</groupId>
    <artifactId>openai</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'ai.skew:openai:1.0.0'
```

## Usage

Change one line of code:

```java
// Before
import com.theokanning.openai.service.OpenAiService;

// After
import ai.skew.openai.OpenAiService;

OpenAiService service = new OpenAiService(apiKey);

// Everything works exactly the same!
ChatCompletionRequest request = ChatCompletionRequest.builder()
    .model("gpt-4o")
    .messages(List.of(new ChatMessage("user", "Hello!")))
    .build();

ChatCompletionResult result = service.createChatCompletion(request);
```

That's it. No configuration needed.

## What It Does

### Telemetry (Always On)
- Tracks token usage, cost, and latency
- Privacy-preserving (no prompts sent by default)
- Zero performance impact (async)
- Never breaks your app (fail-safe)

### Cost Optimization (Opt-In)
When you enable policies in the SKEW dashboard:
- Automatic model downgrading for simple queries
- Retry storm suppression
- Semantic caching
- Token optimization

## Configuration

### Required
```bash
export SKEW_API_KEY=sk_live_...  # Get from dashboard.skew.ai
```

### Optional
```bash
export SKEW_PROXY_ENABLED=true  # Enable when policies require routing
export SKEW_BASE_URL=https://api.skew.ai/v1/openai  # Custom proxy URL
```

## Migration Path

1. **Add dependency** - Update pom.xml or build.gradle
2. **Replace import** - Change package to `ai.skew.openai`
3. **Set API key** - `export SKEW_API_KEY=sk_live_...`
4. **See savings** - Visit dashboard.skew.ai
5. **Enable policies** - When ready, `export SKEW_PROXY_ENABLED=true`

## Guarantees

✅ Drop-in replacement - works identically  
✅ No behavior changes without opt-in  
✅ Fail-safe - errors don't break your app  
✅ Reversible - remove anytime  
✅ Privacy-first - no prompts sent by default  

## License

MIT
