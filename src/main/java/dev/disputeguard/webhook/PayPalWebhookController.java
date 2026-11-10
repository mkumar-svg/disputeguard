package dev.disputeguard.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.disputeguard.config.PayPalProperties;
import dev.disputeguard.config.TopicProperties;
import dev.disputeguard.events.DisputeEventPublisher;
import dev.disputeguard.paypal.PayPalClient;
import dev.disputeguard.paypal.WebhookHeaders;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives PayPal webhooks, verifies them, drops duplicates and publishes dispute events to Kafka.
 * Heavy work (fetching details, the AI agent) happens downstream, so PayPal gets a fast 200.
 */
@RestController
public class PayPalWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookController.class);
    private static final Duration DEDUPE_TTL = Duration.ofDays(3);

    private final PayPalClient paypal;
    private final PayPalProperties props;
    private final TopicProperties topics;
    private final DisputeEventPublisher events;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public PayPalWebhookController(PayPalClient paypal, PayPalProperties props, TopicProperties topics,
                                   DisputeEventPublisher events, StringRedisTemplate redis,
                                   ObjectMapper mapper) {
        this.paypal = paypal;
        this.props = props;
        this.topics = topics;
        this.events = events;
        this.redis = redis;
        this.mapper = mapper;
    }

    @PostMapping("/webhooks/paypal")
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime)
            throws Exception {

        // 1. Verify the delivery really came from PayPal.
        if (!props.skipWebhookVerification()) {
            var headers = new WebhookHeaders(authAlgo, certUrl, transmissionId, transmissionSig, transmissionTime);
            if (!paypal.verifyWebhookSignature(headers, rawBody)) {
                log.warn("Rejected webhook with invalid signature (transmission {})", transmissionId);
                return ResponseEntity.badRequest().build();
            }
        }

        JsonNode event = mapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String eventType = event.path("event_type").asText();

        // 2. Route: only dispute events matter to us.
        String topic = switch (eventType) {
            case "CUSTOMER.DISPUTE.CREATED" -> topics.disputeCreated();
            case "CUSTOMER.DISPUTE.UPDATED", "CUSTOMER.DISPUTE.RESOLVED" -> topics.disputeUpdated();
            default -> null;
        };
        if (topic == null) {
            log.debug("Ignoring webhook {} of type {}", eventId, eventType);
            return ResponseEntity.ok().build();
        }

        // 3. PayPal retries deliveries, so skip events we have already accepted.
        String dedupeKey = "webhook:seen:" + eventId;
        Boolean firstTime = redis.opsForValue().setIfAbsent(dedupeKey, "1", DEDUPE_TTL);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.info("Duplicate webhook {} ignored", eventId);
            return ResponseEntity.ok().build();
        }

        // 4. Publish, keyed by dispute id so all events of one dispute stay in order.
        String disputeId = event.path("resource").path("dispute_id").asText(eventId);
        try {
            events.publish(topic, disputeId, rawBody);
        } catch (Exception e) {
            // Let PayPal retry: forget the event and answer with an error.
            redis.delete(dedupeKey);
            log.error("Failed to publish webhook {} to {}", eventId, topic, e);
            return ResponseEntity.internalServerError().build();
        }

        log.info("Accepted {} for dispute {} -> {}", eventType, disputeId, topic);
        return ResponseEntity.ok().build();
    }
}
