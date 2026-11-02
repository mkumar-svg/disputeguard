package dev.disputeguard.order;

import com.fasterxml.jackson.databind.JsonNode;
import dev.disputeguard.dispute.DisputeRepository;
import dev.disputeguard.paypal.PayPalClient;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The demo store: create PayPal orders, capture them, and record simulated fulfilment. */
@Service
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    private final PayPalClient paypal;
    private final StoreOrderRepository orders;
    private final DemoFulfillmentSeeder seeder;
    private final DisputeRepository disputes;
    private final OrderLinker linker;
    private final String returnUrl;
    private final String cancelUrl;

    public StoreService(PayPalClient paypal, StoreOrderRepository orders, DemoFulfillmentSeeder seeder,
                        DisputeRepository disputes, OrderLinker linker,
                        @Value("${disputeguard.store.return-url}") String returnUrl,
                        @Value("${disputeguard.store.cancel-url}") String cancelUrl) {
        this.paypal = paypal;
        this.orders = orders;
        this.seeder = seeder;
        this.disputes = disputes;
        this.linker = linker;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    /** Creates a PayPal order and returns it with the link where the sandbox buyer approves it. */
    @Transactional
    public CreatedOrder create(BigDecimal amount, String description, String scenario) {
        DemoScenario.parse(scenario); // fail fast on an unknown scenario
        JsonNode res = paypal.createOrder(amount, "USD", description, returnUrl, cancelUrl);
        String approveLink = null;
        for (JsonNode link : res.path("links")) {
            String rel = link.path("rel").asText();
            if ("payer-action".equals(rel) || "approve".equals(rel)) {
                approveLink = link.path("href").asText();
            }
        }
        StoreOrder order = orders.save(new StoreOrder(res.path("id").asText(), amount, "USD", description,
                DemoScenario.parse(scenario).name()));
        log.info("Created demo order {} ({} USD, scenario {})", order.getPaypalOrderId(), amount, order.getScenario());
        return new CreatedOrder(order.getId(), order.getPaypalOrderId(), approveLink);
    }

    /** Captures an approved order, records the simulated fulfilment, and links any waiting dispute. */
    @Transactional
    public StoreOrder capture(String paypalOrderId) {
        StoreOrder order = orders.findByPaypalOrderId(paypalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order " + paypalOrderId));
        if (order.getCaptureId() == null) {
            applyPayPalOrder(order, paypal.captureOrder(paypalOrderId));
        }
        return finish(order);
    }

    /**
     * Brings in an order that was created and captured outside the app (for example with the
     * sandbox scripts), so its dispute can be linked to it.
     */
    @Transactional
    public StoreOrder importOrder(String paypalOrderId, String description, String scenario) {
        DemoScenario parsed = DemoScenario.parse(scenario);
        JsonNode res = paypal.getOrder(paypalOrderId);
        JsonNode unit = res.path("purchase_units").path(0);
        BigDecimal amount = new BigDecimal(unit.path("amount").path("value").asText("0"));
        String currency = unit.path("amount").path("currency_code").asText("USD");
        String desc = description != null ? description : unit.path("description").asText(null);

        StoreOrder order = orders.findByPaypalOrderId(paypalOrderId)
                .orElseGet(() -> new StoreOrder(paypalOrderId, amount, currency, desc, parsed.name()));
        order.setScenario(parsed.name());
        if (desc != null) {
            order.setDescription(desc);
        }
        applyPayPalOrder(order, res);
        return finish(order);
    }

    public List<StoreOrder> list() {
        return orders.findAllByOrderByCreatedAtDesc();
    }

    private StoreOrder finish(StoreOrder order) {
        StoreOrder saved = orders.save(order);
        if (saved.getCaptureId() != null) {
            seeder.seed(saved);
            relinkDisputes();
        }
        return saved;
    }

    /** Reads status, capture id, buyer email and shipping address from an Orders v2 response. */
    private void applyPayPalOrder(StoreOrder order, JsonNode res) {
        order.setStatus(res.path("status").asText(order.getStatus()));
        JsonNode unit = res.path("purchase_units").path(0);
        JsonNode capture = unit.path("payments").path("captures").path(0);
        if (capture.hasNonNull("id")) {
            order.setCaptureId(capture.get("id").asText());
            String time = capture.path("create_time").asText(null);
            order.setCapturedAt(time != null ? OffsetDateTime.parse(time) : OffsetDateTime.now());
        }
        String email = res.path("payer").path("email_address").asText(null);
        if (email != null) {
            order.setBuyerEmail(email);
        }
        JsonNode shipping = unit.path("shipping");
        if (shipping.has("address")) {
            JsonNode a = shipping.get("address");
            List<String> parts = new ArrayList<>();
            for (String f : List.of("address_line_1", "address_line_2", "admin_area_2", "admin_area_1", "postal_code",
                    "country_code")) {
                if (a.hasNonNull(f) && !a.get(f).asText().isBlank()) {
                    parts.add(a.get(f).asText());
                }
            }
            String name = shipping.path("name").path("full_name").asText("");
            order.setShippingAddress((name.isBlank() ? "" : name + ", ") + String.join(", ", parts));
        }
    }

    private void relinkDisputes() {
        disputes.findAll().stream()
                .filter(d -> d.getOrderId() == null)
                .forEach(d -> linker.findOrderFor(d).ifPresent(o -> {
                    d.setOrderId(o.getId());
                    disputes.save(d);
                    log.info("Linked dispute {} to order {}", d.getPaypalDisputeId(), o.getPaypalOrderId());
                }));
    }

    public record CreatedOrder(Long id, String paypalOrderId, String approveLink) {
    }
}
