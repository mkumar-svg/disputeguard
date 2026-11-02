package dev.disputeguard.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.disputeguard.evidence.EvidencePackService;
import dev.disputeguard.evidence.EvidencePackView;
import dev.disputeguard.order.OrderDetails;
import dev.disputeguard.order.OrderDetailsService;
import dev.disputeguard.paypal.PayPalClient;
import dev.disputeguard.submit.SubmitService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** API for the merchant dashboard: list disputes, see one with its evidence pack, re-analyze, approve. */
@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private final DisputeRepository disputes;
    private final DisputeSyncService sync;
    private final PayPalClient paypal;
    private final EvidencePackService evidence;
    private final OrderDetailsService orders;
    private final SubmitService submit;
    private final ObjectMapper mapper;

    public DisputeController(DisputeRepository disputes, DisputeSyncService sync, PayPalClient paypal,
                             EvidencePackService evidence, OrderDetailsService orders, SubmitService submit,
                             ObjectMapper mapper) {
        this.disputes = disputes;
        this.sync = sync;
        this.paypal = paypal;
        this.evidence = evidence;
        this.orders = orders;
        this.submit = submit;
        this.mapper = mapper;
    }

    /** Inbox: every dispute with its recommendation and win chance, newest first. */
    @GetMapping
    public List<InboxRow> list() {
        return disputes.findAllByOrderByCreatedAtDesc().stream()
                .map(d -> {
                    var pack = evidence.latest(d).orElse(null);
                    return new InboxRow(DisputeView.from(d),
                            pack == null ? null : pack.getStatus(),
                            pack == null ? null : pack.getRecommendation(),
                            pack == null ? null : pack.getWinProbability());
                })
                .toList();
    }

    /** One dispute with its order, shipment, chat, evidence pack and the actions PayPal allows now. */
    @GetMapping("/{paypalDisputeId}")
    public DisputeDetails get(@PathVariable String paypalDisputeId) {
        return details(find(paypalDisputeId));
    }

    /** Re-sync one dispute from PayPal (e.g. PP-D-12345). */
    @PostMapping("/{paypalDisputeId}/sync")
    public DisputeView syncOne(@PathVariable String paypalDisputeId) {
        return DisputeView.from(sync.sync(paypalDisputeId));
    }

    /** Pull every dispute PayPal lists for this merchant: a fallback when webhooks are not set up. */
    @PostMapping("/sync")
    public List<DisputeView> syncAll() {
        JsonNode items = paypal.listDisputes().path("items");
        List<DisputeView> out = new ArrayList<>();
        for (JsonNode item : items) {
            out.add(DisputeView.from(sync.sync(item.path("dispute_id").asText())));
        }
        return out;
    }

    /** Build (or rebuild) the evidence pack with the AI agent. */
    @PostMapping("/{paypalDisputeId}/analyze")
    public DisputeDetails analyze(@PathVariable String paypalDisputeId) throws Exception {
        Dispute d = sync.sync(paypalDisputeId);
        evidence.generate(d);
        return details(d);
    }

    /** The merchant approves: DisputeGuard sends evidence, an offer, or accepts the claim on PayPal. */
    @PostMapping("/{paypalDisputeId}/approve")
    public ResponseEntity<SubmitService.SubmitResult> approve(@PathVariable String paypalDisputeId,
                                                              @RequestBody SubmitService.ApproveRequest req) {
        SubmitService.SubmitResult result = submit.approve(find(paypalDisputeId), req);
        return ResponseEntity.status(result.ok() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY).body(result);
    }

    private Dispute find(String paypalDisputeId) {
        return disputes.findByPaypalDisputeId(paypalDisputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown dispute " + paypalDisputeId + ". Try POST /api/disputes/sync first."));
    }

    private DisputeDetails details(Dispute d) {
        Dispute fresh = disputes.findById(d.getId()).orElse(d);
        return new DisputeDetails(
                DisputeView.from(fresh),
                orders.byId(fresh.getOrderId()).orElse(null),
                evidence.latest(fresh).map(p -> EvidencePackView.from(p, mapper)).orElse(null),
                submit.availableActions(fresh));
    }

    public record InboxRow(DisputeView dispute, String packStatus, String recommendation,
                           java.math.BigDecimal winProbability) {
    }

    public record DisputeDetails(DisputeView dispute, OrderDetails order, EvidencePackView evidencePack,
                                 List<String> availableActions) {
    }
}
