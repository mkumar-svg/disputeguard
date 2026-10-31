package dev.disputeguard.dispute;

import com.fasterxml.jackson.databind.JsonNode;
import dev.disputeguard.paypal.PayPalClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read API for the merchant dashboard, plus a manual sync for when webhooks can't reach the app. */
@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private final DisputeRepository disputes;
    private final DisputeSyncService sync;
    private final PayPalClient paypal;

    public DisputeController(DisputeRepository disputes, DisputeSyncService sync, PayPalClient paypal) {
        this.disputes = disputes;
        this.sync = sync;
        this.paypal = paypal;
    }

    @GetMapping
    public List<DisputeView> list() {
        return disputes.findAllByOrderByCreatedAtDesc().stream().map(DisputeView::from).toList();
    }

    /** Sync one dispute by its PayPal id (e.g. PP-D-12345). */
    @PostMapping("/{paypalDisputeId}/sync")
    public DisputeView syncOne(@PathVariable String paypalDisputeId) {
        return DisputeView.from(sync.sync(paypalDisputeId));
    }

    /** Pull every dispute PayPal lists for this merchant: a fallback when webhooks are not set up yet. */
    @PostMapping("/sync")
    public List<DisputeView> syncAll() {
        JsonNode items = paypal.listDisputes().path("items");
        List<DisputeView> out = new ArrayList<>();
        for (JsonNode item : items) {
            out.add(DisputeView.from(sync.sync(item.path("dispute_id").asText())));
        }
        return out;
    }
}
