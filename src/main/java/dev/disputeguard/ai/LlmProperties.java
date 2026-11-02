package dev.disputeguard.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the LLM behind the dispute agent. Any OpenAI-compatible chat-completions endpoint
 * with tool calling works (Claude and Gemini both offer one). No API key means the agent is off.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        int maxTokens,
        int maxSteps,
        int timeoutSeconds) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
