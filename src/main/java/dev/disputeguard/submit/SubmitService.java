package dev.disputeguard.submit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.disputeguard.dispute.Dispute;
import dev.disputeguard.dispute.DisputeSyncService;
import dev.disputeguard.evidence.EvidencePack;
import dev.disputeguard.evidence.EvidencePackRepository;
import dev.disputeguard.evidence.Recommendation;
import dev.disputeguard.order.OrderDetails;
import dev.disputeguard.order.OrderDetailsService;
import dev.disputeguard.paypal.PayPalClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends the merchant's approved decision to PayPal. This is the only place DisputeGuard writes to a
 * dispute, and it runs only when the merchant presses Approve on the dashboard.
 */
@Service
public class SubmitService {

    private static final Logger log = LoggerFactory.getLogger(SubmitService.class);

    private final PayPalClient paypal;
    private final EvidencePackRepository packs;
    private final OrderDetailsService orders;
    private final DisputeSyncService sync;
    private final ObjectMapper mapper;

    public SubmitService(PayPalClient paypal, EvidencePackRepository packs, OrderDetailsService orders,
                         DisputeSyncService sync, ObjectMapper mapper) {
        this.paypal = paypal;
        this.packs = packs;
        this.orders = orders;
        this.sync = sync;
        this.mapper = mapper;
    }

    /** Which write actions PayPal currently allows on this dispute, read from its HATEOAS links. */
    public List<String> availableActions(Dispute dispute) {
        List<String> actions = new ArrayList<>();
        try {
            JsonNode raw = mapper.readTree(dispute.getRawJson() == null ? "{}" : dispute.getRawJson());
            for (JsonNode link : raw.path("links")) {
                String href = link.path("href").asText();
                if (href.endsWith("/provide-evidence")) {
                    actions.add(Recommendation.FIGHT.name());
                } else if (href.endsWith("/make-offer")) {
                    actions.add(Recommendation.OFFER_PARTIAL_REFUND.name());
                } else if (href.endsWith("/accept-claim")) {
                    actions.add(Recommendation.ACCEPT.name());
                }
            }
        } catch (Exception e) {
            log.warn("Could not read links of dispute {}", dispute.getPaypalDisputeId());
        }
        return actions;
    }

    public SubmitResult approve(Dispute dispute, ApproveRequest req) {
        EvidencePack pack = packs.findFirstByDisputeIdOrderByCreatedAtDesc(dispute.getId())
                .orElseThrow(() -> new IllegalStateException("No evidence pack yet for " + dispute.getPaypalDisputeId()));
        if (EvidencePack.SUBMITTED.equals(pack.getStatus())) {
            throw new IllegalStateException("This dispute was already answered");
        }
        Recommendation action = req.action() != null ? req.action() : Recommendation.valueOf(pack.getRecommendation());
        if (!availableActions(dispute).contains(action.name())) {
            throw new IllegalStateException("PayPal does not allow " + action + " on this dispute right now (status "
                    + dispute.getStatus() + ")");
        }
        String note = req.note() != null && !req.note().isBlank() ? req.note().trim() : pack.getDraftNote();
        if (note.length() > 2000) {
            note = note.substring(0, 2000);
        }

        pack.setApprovedBy(req.approvedBy() == null ? "merchant" : req.approvedBy());
        pack.setSubmitAction(action.name());
        try {
            JsonNode res = switch (action) {
                case FIGHT -> paypal.provideEvidence(dispute.getPaypalDisputeId(), evidenceInput(dispute, note));
                case ACCEPT -> paypal.acceptClaim(dispute.getPaypalDisputeId(), note);
                case OFFER_PARTIAL_REFUND -> paypal.makeOffer(dispute.getPaypalDisputeId(), note,
                        offerAmount(req, pack, dispute), dispute.getCurrency() == null ? "USD" : dispute.getCurrency());
            };
            pack.setStatus(EvidencePack.SUBMITTED);
            pack.setSubmittedAt(OffsetDateTime.now());
            pack.setDraftNote(note);
            pack.setSubmitResponse(res == null ? "{}" : res.toString());
            packs.save(pack);
            log.info("Submitted {} for dispute {}", action, dispute.getPaypalDisputeId());
        } catch (RestClientResponseException e) {
            pack.setStatus(EvidencePack.SUBMIT_FAILED);
            pack.setSubmitResponse(e.getResponseBodyAsString());
            packs.save(pack);
            log.warn("PayPal rejected {} for {}: {} {}", action, dispute.getPaypalDisputeId(),
                    e.getStatusCode(), e.getResponseBodyAsString());
            return new SubmitResult(false, action.name(), e.getResponseBodyAsString());
        }

        // Refresh status (e.g. UNDER_REVIEW) so the dashboard shows what PayPal did next.
        try {
            sync.sync(dispute.getPaypalDisputeId());
        } catch (Exception e) {
            log.warn("Re-sync after submit failed: {}", e.getMessage());
        }
        return new SubmitResult(true, action.name(), pack.getSubmitResponse());
    }

    /** provide-evidence input: proof of fulfilment with tracking when we have it, plus the note. */
    private JsonNode evidenceInput(Dispute dispute, String note) {
        ObjectNode input = mapper.createObjectNode();
        ObjectNode evidence = input.putArray("evidences").addObject();
        evidence.put("evidence_type", "PROOF_OF_FULFILLMENT");
        OrderDetails order = orders.byId(dispute.getOrderId()).orElse(null);
        if (order != null && order.shipment() != null && order.shipment().trackingNo() != null) {
            ObjectNode tracking = evidence.putObject("evidence_info").putArray("tracking_info").addObject();
            tracking.put("carrier_name", order.shipment().carrier());
            tracking.put("tracking_number", order.shipment().trackingNo());
        }
        evidence.put("notes", note);
        return input;
    }

    private BigDecimal offerAmount(ApproveRequest req, EvidencePack pack, Dispute dispute) {
        if (req.offerAmount() != null) {
            return req.offerAmount();
        }
        if (pack.getSuggestedRefund() != null) {
            return pack.getSuggestedRefund();
        }
        return dispute.getAmount();
    }

    public record ApproveRequest(Recommendation action, String note, BigDecimal offerAmount, String approvedBy) {
    }

    public record SubmitResult(boolean ok, String action, String paypalResponse) {
    }
}
