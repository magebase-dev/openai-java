# langmesh OpenAI (Java)

Drop-in replacement for the OpenAI Java client with automatic cost optimization and telemetry.

## Installation

### Maven

```xml
<dependency>
    <groupId>ai.langmesh</groupId>
    <artifactId>openai</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'ai.langmesh:openai:1.0.0'
```

## Usage

Change one line of code:

```java
// Before
import com.theokanning.openai.service.OpenAiService;

// After
import ai.langmesh.openai.OpenAiService;

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

When you enable policies in the langmesh dashboard:

- Automatic model downgrading for simple queries
- Retry storm suppression
- Exact Cache (identical requests)
- Semantic Deduplication (high-threshold reuse)
- Semantic Answer Cache (advanced, opt-in)
- Token optimization

## Configuration

### Required

```bash
export langmesh_API_KEY=sk_live_...  # Get from dashboard.langmesh.ai
```

### Optional

```bash
export langmesh_PROXY_ENABLED=true  # Enable when policies require routing
export langmesh_BASE_URL=https://api.langmesh.ai/v1/openai  # Custom proxy URL
```

## Migration Path

1. **Add dependency** - Update pom.xml or build.gradle
2. **Replace import** - Change package to `ai.langmesh.openai`
3. **Set API key** - `export langmesh_API_KEY=sk_live_...`
4. **See savings** - Visit dashboard.langmesh.ai
5. **Enable policies** - When ready, `export langmesh_PROXY_ENABLED=true`

## Guarantees

✅ Drop-in replacement - works identically
✅ No behavior changes without opt-in
✅ Fail-safe - errors don't break your app
✅ Reversible - remove anytime
✅ Privacy-first - no prompts sent by default

## License

MIT
