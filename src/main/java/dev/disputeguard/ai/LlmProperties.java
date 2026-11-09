package dev.disputeguard.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        String fallbackModel,
        int maxTokens,
        int maxSteps,
        int timeoutSeconds) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}