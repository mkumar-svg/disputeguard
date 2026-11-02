package dev.disputeguard.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.disputeguard.config.PayPalProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the PayPal REST APIs DisputeGuard uses: OAuth, Webhooks, Orders v2 and Disputes.
 * The OAuth access token is cached in Redis until shortly before it expires.
 * Failed calls throw {@link org.springframework.web.client.RestClientResponseException} with PayPal's error body.
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

    private String bearer() {
        return "Bearer " + accessToken();
    }

    // ---- Webhooks ----------------------------------------------------------------------------

    /** POST /v1/notifications/verify-webhook-signature: is this webhook delivery genuine? */
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
                    .header(HttpHeaders.AUTHORIZATION, bearer())
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

    // ---- Orders v2 (demo store) --------------------------------------------------------------

    /** POST /v2/checkout/orders: an order the sandbox buyer approves at the returned payer-action link. */
    public JsonNode createOrder(BigDecimal amount, String currency, String description,
                                String returnUrl, String cancelUrl) {
        ObjectNode body = mapper.createObjectNode();
        body.put("intent", "CAPTURE");
        ObjectNode unit = body.putArray("purchase_units").addObject();
        unit.put("description", description);
        unit.putObject("amount").put("currency_code", currency).put("value", amount.toPlainString());
        ObjectNode ctx = body.putObject("payment_source").putObject("paypal").putObject("experience_context");
        ctx.put("brand_name", "DisputeGuard Demo Store");
        ctx.put("user_action", "PAY_NOW");
        ctx.put("shipping_preference", "GET_FROM_FILE");
        ctx.put("return_url", returnUrl);
        ctx.put("cancel_url", cancelUrl);

        return http.post()
                .uri("/v2/checkout/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    /** POST /v2/checkout/orders/{id}/capture: moves the money once the buyer has approved. */
    public JsonNode captureOrder(String paypalOrderId) {
        return http.post()
                .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(JsonNode.class);
    }

    /** GET /v2/checkout/orders/{id}: order status, captures, payer and shipping details. */
    public JsonNode getOrder(String paypalOrderId) {
        return http.get()
                .uri("/v2/checkout/orders/{id}", paypalOrderId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class);
    }

    // ---- Disputes ----------------------------------------------------------------------------

    /** GET /v1/customer/disputes/{id}: full dispute details (reason, amount, status, deadline, links). */
    public JsonNode getDispute(String disputeId) {
        return http.get()
                .uri("/v1/customer/disputes/{id}", disputeId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class);
    }

    /** GET /v1/customer/disputes: the merchant's disputes, newest first. */
    public JsonNode listDisputes() {
        return http.get()
                .uri("/v1/customer/disputes")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * POST /v1/customer/disputes/{id}/provide-evidence (multipart/form-data).
     * The {@code input} part carries the JSON evidence, e.g.
     * {"evidences":[{"evidence_type":"PROOF_OF_FULFILLMENT","evidence_info":{"tracking_info":[...]},"notes":"..."}]}.
     */
    public JsonNode provideEvidence(String disputeId, JsonNode input) {
        HttpHeaders jsonPart = new HttpHeaders();
        jsonPart.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("input", new HttpEntity<>(input.toString(), jsonPart));

        return http.post()
                .uri("/v1/customer/disputes/{id}/provide-evidence", disputeId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(JsonNode.class);
    }

    /** POST /v1/customer/disputes/{id}/accept-claim: the merchant concedes and refunds the buyer. */
    public JsonNode acceptClaim(String disputeId, String note) {
        ObjectNode body = mapper.createObjectNode();
        body.put("note", note);
        return http.post()
                .uri("/v1/customer/disputes/{id}/accept-claim", disputeId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    /** POST /v1/customer/disputes/{id}/make-offer: offer the buyer a (partial) refund to settle. */
    public JsonNode makeOffer(String disputeId, String note, BigDecimal amount, String currency) {
        ObjectNode body = mapper.createObjectNode();
        body.put("note", note);
        body.put("offer_type", "REFUND");
        body.putObject("offer_amount").put("currency_code", currency).put("value", amount.toPlainString());
        return http.post()
                .uri("/v1/customer/disputes/{id}/make-offer", disputeId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}
