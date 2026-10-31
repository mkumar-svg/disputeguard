package dev.disputeguard.dispute;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Enrichment worker: consumes dispute events and syncs full dispute details from PayPal.
 * Next step (Days 4-9): load the matching order, tracking and chats, then hand the case to the AI agent.
 */
@Component
public class DisputeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DisputeEventListener.class);

    private final DisputeSyncService sync;

    public DisputeEventListener(DisputeSyncService sync) {
        this.sync = sync;
    }

    @KafkaListener(topics = {"${disputeguard.topics.dispute-created}", "${disputeguard.topics.dispute-updated}"})
    public void onDisputeEvent(ConsumerRecord<String, String> record) {
        String disputeId = record.key();
        log.info("Received {} for dispute {}", record.topic(), disputeId);
        sync.sync(disputeId);
        // TODO(day 4+): if new and waiting for seller response, build the evidence pack with the AI agent.
    }
}
