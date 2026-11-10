package dev.disputeguard.events;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Default: publish to Kafka; DisputeEventListener consumes it. */
@Component
@ConditionalOnProperty(name = "disputeguard.events.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaDisputeEventPublisher implements DisputeEventPublisher {

    private final KafkaTemplate<String, String> kafka;

    public KafkaDisputeEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(String topic, String disputeId, String rawBody) throws Exception {
        kafka.send(topic, disputeId, rawBody).get(5, TimeUnit.SECONDS);
    }
}
