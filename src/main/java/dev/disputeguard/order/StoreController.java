package dev.disputeguard.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-store API: place a sandbox order, capture it after the buyer approves, or import an existing one. */
@RestController
@RequestMapping("/api/store/orders")
public class StoreController {

    private final StoreService store;
    private final OrderDetailsService details;

    public StoreController(StoreService store, OrderDetailsService details) {
        this.store = store;
        this.details = details;
    }

    @PostMapping
    public StoreService.CreatedOrder create(@Valid @RequestBody CreateOrderRequest req) {
        return store.create(req.amount(), req.description(), req.scenario());
    }

    @PostMapping("/{paypalOrderId}/capture")
    public OrderDetails capture(@PathVariable String paypalOrderId) {
        return details.of(store.capture(paypalOrderId));
    }

    @PostMapping("/import")
    public OrderDetails importOrder(@Valid @RequestBody ImportOrderRequest req) {
        return details.of(store.importOrder(req.paypalOrderId(), req.description(), req.scenario()));
    }

    @GetMapping
    public List<OrderSummary> list() {
        return store.list().stream().map(OrderSummary::from).toList();
    }

    public record CreateOrderRequest(
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank String description,
            String scenario) {
    }

    public record ImportOrderRequest(@NotBlank String paypalOrderId, String description, String scenario) {
    }

    public record OrderSummary(Long id, String paypalOrderId, String captureId, BigDecimal amount, String currency,
                               String description, String status, String scenario, OffsetDateTime createdAt) {
        static OrderSummary from(StoreOrder o) {
            return new OrderSummary(o.getId(), o.getPaypalOrderId(), o.getCaptureId(), o.getAmount(), o.getCurrency(),
                    o.getDescription(), o.getStatus(), o.getScenario(), o.getCreatedAt());
        }
    }
}
