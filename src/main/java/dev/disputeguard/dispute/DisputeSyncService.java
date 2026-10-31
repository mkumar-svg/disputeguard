package dev.disputeguard.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import dev.disputeguard.paypal.PayPalClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pulls the latest state of a dispute from PayPal and stores it locally. */
@Service
public class DisputeSyncService {

    private static final Logger log = LoggerFactory.getLogger(DisputeSyncService.class);

    private final PayPalClient paypal;
    private final DisputeRepository disputes;

    public DisputeSyncService(PayPalClient paypal, DisputeRepository disputes) {
        this.paypal = paypal;
        this.disputes = disputes;
    }

    @Transactional
    public Dispute sync(String paypalDisputeId) {
        JsonNode d = paypal.getDispute(paypalDisputeId);

        Dispute dispute = disputes.findByPaypalDisputeId(paypalDisputeId)
                .orElseGet(() -> new Dispute(paypalDisputeId));

        dispute.setReason(text(d, "reason"));
        dispute.setStatus(text(d, "status"));

        JsonNode amount = d.path("dispute_amount");
        if (amount.hasNonNull("value")) {
            dispute.setAmount(new BigDecimal(amount.get("value").asText()));
            dispute.setCurrency(text(amount, "currency_code"));
        }

        String due = text(d, "seller_response_due_date");
        if (due != null) {
            dispute.setSellerResponseDue(OffsetDateTime.parse(due));
        }

        JsonNode tx = d.path("disputed_transactions").path(0);
        dispute.setTransactionId(text(tx, "seller_transaction_id"));
        dispute.setRawJson(d.toString());

        Dispute saved = disputes.save(dispute);
        log.info("Synced dispute {} reason={} status={} due={}",
                paypalDisputeId, saved.getReason(), saved.getStatus(), saved.getSellerResponseDue());
        return saved;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
