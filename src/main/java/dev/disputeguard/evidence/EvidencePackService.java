package dev.disputeguard.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.disputeguard.ai.AgentResult;
import dev.disputeguard.ai.DisputeAgent;
import dev.disputeguard.dispute.Dispute;
import dev.disputeguard.order.OrderDetails;
import dev.disputeguard.order.OrderDetailsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the evidence pack for a dispute: checklist and base win chance from rules, then the AI agent
 * for the summary, a small score adjustment and the response note. If the agent is off or fails, the
 * pack is still produced from rules, so the merchant is never left without a draft.
 */
@Service
public class EvidencePackService {

    private static final Logger log = LoggerFactory.getLogger(EvidencePackService.class);

    private final EvidenceChecklist checklist;
    private final DisputeAgent agent;
    private final OrderDetailsService orders;
    private final EvidencePackRepository packs;
    private final ObjectMapper mapper;

    public EvidencePackService(EvidenceChecklist checklist, DisputeAgent agent, OrderDetailsService orders,
                               EvidencePackRepository packs, ObjectMapper mapper) {
        this.checklist = checklist;
        this.agent = agent;
        this.orders = orders;
        this.packs = packs;
        this.mapper = mapper;
    }

    /** Latest pack for a dispute, if any. */
    public java.util.Optional<EvidencePack> latest(Dispute dispute) {
        return packs.findFirstByDisputeIdOrderByCreatedAtDesc(dispute.getId());
    }

    /** Builds a new pack, unless one was already submitted (a submitted pack is never replaced). */
    @Transactional
    public EvidencePack generate(Dispute dispute) throws Exception {
        java.util.Optional<EvidencePack> existing = latest(dispute);
        if (existing.isPresent() && EvidencePack.SUBMITTED.equals(existing.get().getStatus())) {
            return existing.get();
        }

        OrderDetails order = orders.byId(dispute.getOrderId()).orElse(null);
        List<ChecklistItem> items = checklist.build(dispute, order);
        double base = checklist.baseProbability(items);

        AgentResult result = null;
        if (agent.enabled()) {
            try {
                result = agent.analyze(dispute, order, items, base);
            } catch (Exception e) {
                log.warn("Agent failed for dispute {}, using rule-based pack: {}", dispute.getPaypalDisputeId(),
                        e.getMessage());
            }
        }
        if (result == null) {
            result = ruleBased(dispute, order, items);
        }

        double finalP = Math.max(0.02, Math.min(0.98, base + result.probabilityAdjustment()));
        Recommendation rec = Recommendation.forProbability(finalP);
        BigDecimal refund = result.suggestedRefund();
        if (rec == Recommendation.OFFER_PARTIAL_REFUND && refund == null && dispute.getAmount() != null) {
            refund = dispute.getAmount().multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
        }

        EvidencePack pack = existing.orElseGet(() -> new EvidencePack(dispute.getId()));
        pack.setStatus(EvidencePack.READY);
        pack.setReasonSummary(result.reasonSummary());
        pack.setChecklist(mapper.writeValueAsString(items));
        pack.setBaseProbability(BigDecimal.valueOf(base).setScale(3, RoundingMode.HALF_UP));
        pack.setWinProbability(BigDecimal.valueOf(finalP).setScale(3, RoundingMode.HALF_UP));
        pack.setAdjustmentReason(result.adjustmentReason());
        pack.setRecommendation(rec.name());
        pack.setSuggestedRefund(rec == Recommendation.OFFER_PARTIAL_REFUND ? refund : null);
        pack.setDraftNote(result.responseNote());
        pack.setGuardrailNotes(mapper.writeValueAsString(result.guardrailNotes()));
        pack.setAgentTrace(mapper.writeValueAsString(result.steps()));
        pack.setModel(result.model());
        pack.setSubmitResponse(null);
        EvidencePack saved = packs.save(pack);
        log.info("Evidence pack for {}: base={} final={} -> {} (model {})", dispute.getPaypalDisputeId(),
                base, finalP, rec, result.model());
        return saved;
    }

    /** The pack without an LLM: a factual note assembled from the checklist. */
    private AgentResult ruleBased(Dispute dispute, OrderDetails order, List<ChecklistItem> items) {
        StringBuilder note = new StringBuilder();
        if (order == null) {
            note.append("We could not match this payment to an order in our store and are reviewing it.");
        } else {
            note.append("Order ").append(order.paypalOrderId()).append(" (")
                    .append(order.amount()).append(' ').append(order.currency());
            if (order.description() != null) {
                note.append(", ").append(order.description());
            }
            note.append(").");
            for (ChecklistItem item : items) {
                if (!"ORDER".equals(item.key()) && item.status() != ChecklistItem.Status.MISSING) {
                    note.append(' ').append(item.detail()).append('.');
                }
            }
        }
        List<AgentResult.AgentStep> steps = new ArrayList<>();
        steps.add(new AgentResult.AgentStep("rules", "LLM not used; note built from the evidence checklist"));
        String summary = EvidenceChecklist.ITEM_NOT_RECEIVED.equals(dispute.getReason())
                ? "Buyer says the item was not received."
                : "Buyer opened a " + dispute.getReason() + " dispute.";
        return new AgentResult(summary, 0, "No adjustment (rule-based)", note.toString().trim(), null,
                List.of(), steps, "rules");
    }
}
