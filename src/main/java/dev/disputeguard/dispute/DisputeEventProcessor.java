package dev.disputeguard.dispute;

import dev.disputeguard.evidence.EvidencePackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What happens when a dispute event arrives, however it arrives (Kafka locally, in-process on the
 * hosted demo): sync the dispute from PayPal, link its order, and prepare the evidence pack as soon
 * as PayPal is waiting for the seller.
 */
@Component
public class DisputeEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(DisputeEventProcessor.class);
    static final String WAITING_FOR_SELLER = "WAITING_FOR_SELLER_RESPONSE";

    private final DisputeSyncService sync;
    private final EvidencePackService evidence;

    public DisputeEventProcessor(DisputeSyncService sync, EvidencePackService evidence) {
        this.sync = sync;
        this.evidence = evidence;
    }

    public void process(String disputeId, String source) {
        log.info("Received {} for dispute {}", source, disputeId);
        try {
            Dispute dispute = sync.sync(disputeId);
            if (WAITING_FOR_SELLER.equals(dispute.getStatus()) && evidence.latest(dispute).isEmpty()) {
                evidence.generate(dispute);
            }
        } catch (Exception e) {
            // Log and move on: a bad event must not block the others. The dashboard can re-run analysis.
            log.error("Failed to process {} for dispute {}: {}", source, disputeId, e.getMessage(), e);
        }
    }
}
