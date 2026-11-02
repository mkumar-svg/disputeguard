package dev.disputeguard.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** The evidence pack as the dashboard shows it, with the JSON columns parsed. */
public record EvidencePackView(
        Long id,
        String status,
        String recommendation,
        BigDecimal winProbability,
        BigDecimal baseProbability,
        String adjustmentReason,
        String reasonSummary,
        JsonNode checklist,
        String draftNote,
        BigDecimal suggestedRefund,
        JsonNode guardrailNotes,
        JsonNode agentSteps,
        String model,
        String submitAction,
        JsonNode submitResponse,
        OffsetDateTime submittedAt,
        OffsetDateTime updatedAt) {

    public static EvidencePackView from(EvidencePack p, ObjectMapper mapper) {
        return new EvidencePackView(p.getId(), p.getStatus(), p.getRecommendation(), p.getWinProbability(),
                p.getBaseProbability(), p.getAdjustmentReason(), p.getReasonSummary(),
                json(mapper, p.getChecklist()), p.getDraftNote(), p.getSuggestedRefund(),
                json(mapper, p.getGuardrailNotes()), json(mapper, p.getAgentTrace()), p.getModel(),
                p.getSubmitAction(), json(mapper, p.getSubmitResponse()), p.getSubmittedAt(), p.getUpdatedAt());
    }

    private static JsonNode json(ObjectMapper mapper, String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return mapper.getNodeFactory().textNode(s);
        }
    }
}
