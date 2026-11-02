package dev.disputeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal client for an OpenAI-compatible {@code /chat/completions} endpoint with tool calling.
 * Deliberately sends no {@code temperature} and no {@code response_format}: some providers reject
 * them (Claude's compatibility layer returns 400 for both), and the agent validates JSON itself.
 */
@Component
public class LlmClient {

    private final LlmProperties props;
    private final ObjectMapper mapper;
    private final RestClient http;

    public LlmClient(LlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(Math.max(10, props.timeoutSeconds()) * 1000);
        String base = props.baseUrl() == null ? "" : props.baseUrl().replaceAll("/+$", "");
        this.http = RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    public String model() {
        return props.model();
    }

    /** One chat-completions round trip. Returns {@code choices[0].message}. */
    public JsonNode chat(ArrayNode messages, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.model());
        body.put("max_tokens", props.maxTokens());
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        JsonNode res = http.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return res.path("choices").path(0).path("message");
    }
}
