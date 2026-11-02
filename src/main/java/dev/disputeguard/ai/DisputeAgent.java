package dev.disputeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.disputeguard.dispute.Dispute;
import dev.disputeguard.evidence.ChecklistItem;
import dev.disputeguard.order.OrderDetails;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The dispute agent: an LLM that gathers evidence through tools, then returns a JSON verdict.
 *
 * <p>Guardrails: the agent only sees this dispute's own order data; its score change is capped at
 * ±0.15 around the rule-based checklist; any tracking-style number in its note that is not in the
 * data is flagged; and nothing it writes reaches PayPal without the merchant's approval.
 */
@Component
public class DisputeAgent {

    private static final Logger log = LoggerFactory.getLogger(DisputeAgent.class);
    private static final double MAX_ADJUSTMENT = 0.15;
    private static final int MAX_NOTE = 2000;
    private static final Pattern ID_LIKE = Pattern.compile("\\b[A-Z0-9]{10,}\\b");

    private static final String SYSTEM_PROMPT = """
            You are DisputeGuard, an assistant that prepares a merchant's response to a PayPal dispute.

            Work in two phases.
            1. Gather evidence: call the tools get_order, get_tracking and get_chat_logs before you answer.
            2. Answer with ONE JSON object and nothing else (no prose, no code fences):
            {
              "reason_summary": "one sentence: what the buyer claims",
              "probability_adjustment": number between -0.15 and 0.15,
              "adjustment_reason": "one sentence: why you moved the checklist score, or 'No adjustment'",
              "suggested_refund": number or null,
              "response_note": "the note sent to PayPal with the evidence"
            }

            Rules for response_note:
            - Written by the merchant to PayPal, factual and polite, at most 1200 characters.
            - State only facts that appear in the tool results or the dispute: dates, carrier, tracking number,
              delivery scan, messages. Never invent or guess a number, date, address or event.
            - If the evidence is weak, say so honestly instead of overstating it.
            - Do not mention DisputeGuard, AI, checklists or probabilities.

            Rules for probability_adjustment: start from the checklist's base win chance in the user message.
            Adjust only for something the checklist cannot see, for example a buyer message that confirms
            receipt, or a contradiction in the tracking. Most cases need 0.
            Set suggested_refund only when a partial refund looks like the sensible way to settle.
            """;

    private final LlmClient llm;
    private final LlmProperties props;
    private final ObjectMapper mapper;

    public DisputeAgent(LlmClient llm, LlmProperties props, ObjectMapper mapper) {
        this.llm = llm;
        this.props = props;
        this.mapper = mapper;
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** Runs the agent. Throws if the LLM fails or never returns valid JSON; the caller falls back to rules. */
    public AgentResult analyze(Dispute dispute, OrderDetails order, List<ChecklistItem> checklist,
                               double baseProbability) throws Exception {
        ArrayNode messages = mapper.createArrayNode();
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", caseBrief(dispute, checklist, baseProbability));

        List<AgentResult.AgentStep> steps = new ArrayList<>();
        StringBuilder seenData = new StringBuilder(caseBrief(dispute, checklist, baseProbability));
        boolean askedForJson = false;

        for (int step = 0; step < Math.max(2, props.maxSteps()); step++) {
            JsonNode msg = llm.chat(messages, tools());
            JsonNode calls = msg.path("tool_calls");

            if (calls.isArray() && !calls.isEmpty()) {
                ObjectNode assistant = messages.addObject();
                assistant.put("role", "assistant");
                String text = msg.path("content").asText("");
                if (!text.isBlank()) {
                    assistant.put("content", text); // empty text blocks are rejected by some providers
                }
                assistant.set("tool_calls", calls);
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asText();
                    String output = runTool(name, order);
                    seenData.append('\n').append(output);
                    steps.add(new AgentResult.AgentStep(name, summarize(name, order)));
                    messages.addObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.path("id").asText())
                            .put("name", name) // used by some providers, ignored by others
                            .put("content", output);
                }
                continue;
            }

            String content = msg.path("content").asText("");
            JsonNode verdict = extractJson(content);
            if (verdict != null && verdict.hasNonNull("response_note")) {
                return toResult(verdict, steps, seenData.toString());
            }
            if (askedForJson) {
                break;
            }
            askedForJson = true;
            if (!content.isBlank()) {
                messages.addObject().put("role", "assistant").put("content", content);
            }
            messages.addObject().put("role", "user")
                    .put("content", "Reply with only the JSON object described in the instructions.");
        }
        throw new IllegalStateException("Agent did not return a valid verdict");
    }

    // ---- tools -------------------------------------------------------------------------------

    private ArrayNode tools() {
        ArrayNode tools = mapper.createArrayNode();
        tool(tools, "get_order", "The merchant's order behind this dispute: id, amount, item, buyer email, "
                + "shipping address, order and capture dates.");
        tool(tools, "get_tracking", "Shipping record for the order: carrier, tracking number, every tracking scan "
                + "and the delivery date if delivered.");
        tool(tools, "get_chat_logs", "Messages between the buyer and the seller about this order, oldest first.");
        return tools;
    }

    private void tool(ArrayNode tools, String name, String description) {
        ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
        fn.put("name", name);
        fn.put("description", description);
        ObjectNode params = fn.putObject("parameters");
        params.put("type", "object");
        params.putObject("properties");
    }

    private String runTool(String name, OrderDetails order) {
        ObjectNode out = mapper.createObjectNode();
        if (order == null) {
            out.put("error", "No order in the store is linked to this dispute.");
            return out.toString();
        }
        switch (name) {
            case "get_order" -> {
                out.put("order_id", order.paypalOrderId());
                out.put("capture_id", order.captureId());
                out.put("amount", order.amount() + " " + order.currency());
                out.put("item", order.description());
                out.put("buyer_email", order.buyerEmail());
                out.put("shipping_address", order.shippingAddress());
                out.put("captured_at", String.valueOf(order.capturedAt()));
            }
            case "get_tracking" -> {
                if (order.shipment() == null) {
                    out.put("result", "No shipment recorded for this order.");
                } else {
                    out.put("carrier", order.shipment().carrier());
                    out.put("tracking_number", order.shipment().trackingNo());
                    out.put("delivered_at", String.valueOf(order.shipment().deliveredAt()));
                    out.set("scans", order.shipment().events());
                }
            }
            case "get_chat_logs" -> {
                ArrayNode chat = out.putArray("messages");
                order.messages().forEach(m -> chat.addObject()
                        .put("from", m.sender()).put("at", String.valueOf(m.sentAt())).put("text", m.text()));
            }
            default -> out.put("error", "Unknown tool " + name);
        }
        return out.toString();
    }

    private String summarize(String tool, OrderDetails order) {
        if (order == null) {
            return "No linked order";
        }
        return switch (tool) {
            case "get_order" -> "Read order " + order.paypalOrderId() + " (" + order.amount() + " " + order.currency() + ")";
            case "get_tracking" -> order.shipment() == null ? "No shipment on file"
                    : order.shipment().carrier() + " " + order.shipment().trackingNo()
                            + (order.shipment().deliveredAt() != null ? ", delivered" : ", not delivered");
            case "get_chat_logs" -> order.messages().size() + " messages";
            default -> tool;
        };
    }

    // ---- prompt and output -------------------------------------------------------------------

    private String caseBrief(Dispute d, List<ChecklistItem> checklist, double baseProbability) {
        ObjectNode brief = mapper.createObjectNode();
        brief.put("dispute_id", d.getPaypalDisputeId());
        brief.put("reason", d.getReason());
        brief.put("status", d.getStatus());
        brief.put("claimed_amount", d.getAmount() + " " + d.getCurrency());
        brief.put("opened_at", String.valueOf(d.getDisputeCreatedAt()));
        brief.put("seller_response_due", String.valueOf(d.getSellerResponseDue()));
        if (d.getBuyerMessage() != null) {
            brief.put("buyer_message", d.getBuyerMessage());
        }
        ArrayNode items = brief.putArray("checklist");
        checklist.forEach(i -> items.addObject()
                .put("item", i.label()).put("status", i.status().name()).put("detail", i.detail()));
        brief.put("base_win_probability", baseProbability);
        return "Dispute to prepare a response for:\n" + brief.toPrettyString();
    }

    private JsonNode extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return mapper.readTree(content.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private AgentResult toResult(JsonNode v, List<AgentResult.AgentStep> steps, String seenData) {
        List<String> guardrails = new ArrayList<>();
        double adj = v.path("probability_adjustment").asDouble(0);
        if (Math.abs(adj) > MAX_ADJUSTMENT) {
            guardrails.add("Score change " + adj + " capped at ±" + MAX_ADJUSTMENT);
            adj = Math.copySign(MAX_ADJUSTMENT, adj);
        }
        String note = v.path("response_note").asText("").trim();
        if (note.length() > MAX_NOTE) {
            guardrails.add("Note shortened to " + MAX_NOTE + " characters");
            note = note.substring(0, MAX_NOTE);
        }
        Set<String> unknownIds = new LinkedHashSet<>();
        Matcher m = ID_LIKE.matcher(note);
        while (m.find()) {
            if (!seenData.contains(m.group())) {
                unknownIds.add(m.group());
            }
        }
        if (!unknownIds.isEmpty()) {
            guardrails.add("Note mentions numbers not found in the data: " + String.join(", ", unknownIds));
        }
        if (steps.isEmpty()) {
            guardrails.add("Agent answered without calling any tool");
        }
        BigDecimal refund = v.hasNonNull("suggested_refund") && v.get("suggested_refund").isNumber()
                ? v.get("suggested_refund").decimalValue() : null;

        log.info("Agent verdict: adjustment={} steps={} guardrails={}", adj, steps.size(), guardrails);
        return new AgentResult(
                v.path("reason_summary").asText(null),
                adj,
                v.path("adjustment_reason").asText("No adjustment"),
                note,
                refund,
                guardrails,
                steps,
                llm.model());
    }
}
