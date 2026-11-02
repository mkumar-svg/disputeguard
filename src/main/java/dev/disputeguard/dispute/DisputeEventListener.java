package dev.disputeguard.dispute;

import dev.disputeguard.evidence.EvidencePackService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Enrichment worker: consumes dispute events, syncs the dispute from PayPal, links its order, and
 * prepares the evidence pack as soon as PayPal is waiting for the seller.
 */
@Component
public class DisputeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DisputeEventListener.class);
    static final String WAITING_FOR_SELLER = "WAITING_FOR_SELLER_RESPONSE";

    private final DisputeSyncService sync;
    private final EvidencePackService evidence;

    public DisputeEventListener(DisputeSyncService sync, EvidencePackService evidence) {
        this.sync = sync;
        this.evidence = evidence;
    }

    @KafkaListener(topics = {"${disputeguard.topics.dispute-created}", "${disputeguard.topics.dispute-updated}"})
    public void onDisputeEvent(ConsumerRecord<String, String> record) {
        String disputeId = record.key();
        log.info("Received {} for dispute {}", record.topic(), disputeId);
        try {
            Dispute dispute = sync.sync(disputeId);
            if (WAITING_FOR_SELLER.equals(dispute.getStatus()) && evidence.latest(dispute).isEmpty()) {
                evidence.generate(dispute);
            }
        } catch (Exception e) {
            // Log and move on: a bad event must not block the partition. The dashboard can re-run analyze.
            log.error("Failed to process {} for dispute {}: {}", record.topic(), disputeId, e.getMessage(), e);
        }
    }
}
