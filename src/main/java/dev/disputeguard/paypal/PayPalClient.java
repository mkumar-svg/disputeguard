package dev.disputeguard.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.disputeguard.config.PayPalProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the PayPal REST APIs DisputeGuard uses: OAuth, webhook verification and Disputes.
 * The OAuth access token is cached in Redis until shortly before it expires.
 */
@Component
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);
    private static final String TOKEN_KEY = "paypal:token";

    private final PayPalProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RestClient http;

    public PayPalClient(PayPalProperties props, StringRedisTemplate redis, ObjectMapper mapper) {
        this.props = props;
        this.redis = redis;
        this.mapper = mapper;
        this.http = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    // ---- OAuth -------------------------------------------------------------------------------

    public String accessToken() {
        String cached = redis.opsForValue().get(TOKEN_KEY);
        if (cached != null) {
            return cached;
        }
        String basic = Base64.getEncoder().encodeToString(
                (props.clientId() + ":" + props.clientSecret()).getBytes(StandardCharsets.UTF_8));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        JsonNode res = http.post()
                .uri("/v1/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        String token = res.path("access_token").asText();
        long expiresIn = res.path("expires_in").asLong(300);
        // Refresh a minute early so a request never goes out with a token that expires mid-flight.
        redis.opsForValue().set(TOKEN_KEY, token, Duration.ofSeconds(Math.max(30, expiresIn - 60)));
        log.info("Fetched new PayPal access token (expires in {}s)", expiresIn);
        return token;
    }

    // ---- Webhooks ----------------------------------------------------------------------------

    /**
     * Asks PayPal whether a webhook delivery is genuine.
     * See POST /v1/notifications/verify-webhook-signature.
     */
    public boolean verifyWebhookSignature(WebhookHeaders headers, String rawBody) {
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("auth_algo", headers.authAlgo());
            req.put("cert_url", headers.certUrl());
            req.put("transmission_id", headers.transmissionId());
            req.put("transmission_sig", headers.transmissionSig());
            req.put("transmission_time", headers.transmissionTime());
            req.put("webhook_id", props.webhookId());
            req.set("webhook_event", mapper.readTree(rawBody));

            JsonNode res = http.post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(JsonNode.class);

            return "SUCCESS".equals(res.path("verification_status").asText());
        } catch (Exception e) {
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ---- Disputes ----------------------------------------------------------------------------

    /** GET /v1/customer/disputes/{id}: full dispute details (reason, amount, status, deadline). */
    public JsonNode getDispute(String disputeId) {
        return http.get()
                .uri("/v1/customer/disputes/{id}", disputeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(JsonNode.class);
    }

    /** GET /v1/customer/disputes: the merchant's disputes, newest first. */
    public JsonNode listDisputes() {
        return http.get()
                .uri("/v1/customer/disputes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                .retrieve()
                .body(JsonNode.class);
    }

    // provide-evidence, accept-claim and make-offer are added in the submit-service step (Days 9-14).
}
