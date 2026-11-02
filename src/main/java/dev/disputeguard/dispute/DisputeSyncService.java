package dev.disputeguard.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import dev.disputeguard.order.OrderLinker;
import dev.disputeguard.paypal.PayPalClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pulls the latest state of a dispute from PayPal, stores it, and links it to its demo-store order. */
@Service
public class DisputeSyncService {

    private static final Logger log = LoggerFactory.getLogger(DisputeSyncService.class);

    private final PayPalClient paypal;
    private final DisputeRepository disputes;
    private final OrderLinker linker;

    public DisputeSyncService(PayPalClient paypal, DisputeRepository disputes, OrderLinker linker) {
        this.paypal = paypal;
        this.disputes = disputes;
        this.linker = linker;
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

        OffsetDateTime due = time(d, "seller_response_due_date");
        if (due != null) {
            dispute.setSellerResponseDue(due);
        }
        OffsetDateTime created = time(d, "create_time");
        if (created != null) {
            dispute.setDisputeCreatedAt(created);
        }

        JsonNode tx = d.path("disputed_transactions").path(0);
        dispute.setTransactionId(text(tx, "seller_transaction_id"));
        dispute.setBuyerTransactionId(text(tx, "buyer_transaction_id"));
        JsonNode gross = tx.path("gross_amount");
        if (gross.hasNonNull("value")) {
            dispute.setGrossAmount(new BigDecimal(gross.get("value").asText()));
        }

        // The buyer's own words, if PayPal shares them.
        for (JsonNode m : d.path("messages")) {
            if ("BUYER".equals(m.path("posted_by").asText()) && m.hasNonNull("content")) {
                dispute.setBuyerMessage(m.get("content").asText());
                break;
            }
        }

        dispute.setRawJson(d.toString());
        if (dispute.getOrderId() == null) {
            linker.findOrderFor(dispute).ifPresent(o -> dispute.setOrderId(o.getId()));
        }

        Dispute saved = disputes.save(dispute);
        log.info("Synced dispute {} reason={} status={} order={} due={}",
                paypalDisputeId, saved.getReason(), saved.getStatus(), saved.getOrderId(),
                saved.getSellerResponseDue());
        return saved;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static OffsetDateTime time(JsonNode node, String field) {
        String v = text(node, field);
        try {
            return v == null ? null : OffsetDateTime.parse(v);
        } catch (Exception e) {
            return null;
        }
    }
}
