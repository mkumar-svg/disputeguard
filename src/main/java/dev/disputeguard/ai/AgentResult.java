package dev.disputeguard.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the dispute agent concluded.
 *
 * @param reasonSummary         one-sentence summary of the buyer's claim
 * @param probabilityAdjustment change to the checklist's base win chance, within ±0.15
 * @param adjustmentReason      why the agent moved the score (or "No adjustment")
 * @param responseNote          the note to send to PayPal with the evidence (max 2000 characters)
 * @param suggestedRefund       a partial-refund amount if settling makes sense, else null
 * @param guardrailNotes        problems the guardrails found in the agent's output (empty if none)
 * @param steps                 the agent's tool calls, shown on the dashboard
 * @param model                 the model that produced this, or "rules" when the LLM was not used
 */
public record AgentResult(
        String reasonSummary,
        double probabilityAdjustment,
        String adjustmentReason,
        String responseNote,
        BigDecimal suggestedRefund,
        List<String> guardrailNotes,
        List<AgentStep> steps,
        String model) {

    /** One tool call made by the agent. */
    public record AgentStep(String tool, String summary) {
    }
}
