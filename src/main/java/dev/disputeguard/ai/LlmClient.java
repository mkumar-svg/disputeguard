package dev.disputeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Minimal client for an OpenAI-compatible /chat/completions endpoint with tool calling.
 * Uses the JDK HttpClient directly so status, body and network errors are all handled here.
 * Busy providers (429/5xx), network failures and unreadable responses are retried; if the main
 * model stays busy, the optional fallback model is tried.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final long[] BACKOFF_MS = {2_000, 5_000};

    private final LlmProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String endpoint;
    private volatile String lastModel;

    public LlmClient(LlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.lastModel = props.model();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String base = props.baseUrl() == null ? "" : props.baseUrl().replaceAll("/+$", "");
        this.endpoint = base + "/chat/completions";
    }

    public String model() {
        return lastModel;
    }

    public JsonNode chat(ArrayNode messages, ArrayNode tools) {
        List<String> models = new ArrayList<>();
        models.add(props.model());
        if (props.fallbackModel() != null && !props.fallbackModel().isBlank()
                && !props.fallbackModel().equals(props.model())) {
            models.add(props.fallbackModel());
        }
        RuntimeException last = null;
        for (String model : models) {
            for (int attempt = 0; attempt <= BACKOFF_MS.length; attempt++) {
                try {
                    JsonNode message = call(model, messages, tools);
                    lastModel = model;
                    return message;
                } catch (RetryableLlmException e) {
                    last = e;
                    log.warn("LLM {} attempt {} failed: {}", model, attempt + 1, e.getMessage());
                }
                if (attempt < BACKOFF_MS.length) {
                    sleep(BACKOFF_MS[attempt]);
                }
            }
            log.warn("LLM {} still failing after retries", model);
        }
        throw last;
    }

    private JsonNode call(String model, ArrayNode messages, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", props.maxTokens());
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(Math.max(10, props.timeoutSeconds())))
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RetryableLlmException("network error: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the LLM", e);
        }

        int status = response.statusCode();
        String raw = response.body() == null ? "" : response.body();
        String snippet = raw.length() > 400 ? raw.substring(0, 400) + "..." : raw;

        if (status == 429 || status >= 500) {
            throw new RetryableLlmException("HTTP " + status + ": " + snippet, null);
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("LLM returned HTTP " + status + ": " + snippet);
        }
        JsonNode res;
        try {
            res = raw.isBlank() ? null : mapper.readTree(raw);
        } catch (Exception e) {
            res = null;
        }
        if (res == null || !res.path("choices").path(0).has("message")) {
            throw new RetryableLlmException("unexpected response (HTTP " + status + "): "
                    + (snippet.isBlank() ? "<empty body>" : snippet), null);
        }
        return res.path("choices").path(0).path("message");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class RetryableLlmException extends RuntimeException {
        RetryableLlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}