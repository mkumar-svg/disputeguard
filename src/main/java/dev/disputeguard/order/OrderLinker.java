package dev.disputeguard.order;

import dev.disputeguard.dispute.Dispute;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Finds the demo-store order a dispute is about.
 * First by transaction id (PayPal's seller transaction id is normally the capture id), then, as a
 * fallback, by the most recent captured order with the same payment amount placed before the dispute.
 */
@Component
public class OrderLinker {

    private final StoreOrderRepository orders;

    public OrderLinker(StoreOrderRepository orders) {
        this.orders = orders;
    }

    public Optional<StoreOrder> findOrderFor(Dispute dispute) {
        Optional<StoreOrder> byTx = Stream.of(dispute.getTransactionId(), dispute.getBuyerTransactionId())
                .filter(id -> id != null && !id.isBlank())
                .map(orders::findByCaptureId)
                .flatMap(Optional::stream)
                .findFirst();
        if (byTx.isPresent()) {
            return byTx;
        }
        if (dispute.getGrossAmount() == null) {
            return Optional.empty();
        }
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getCaptureId() != null)
                .filter(o -> o.getAmount().compareTo(dispute.getGrossAmount()) == 0)
                .filter(o -> dispute.getDisputeCreatedAt() == null || o.getCapturedAt() == null
                        || o.getCapturedAt().isBefore(dispute.getDisputeCreatedAt()))
                .max(Comparator.comparing(StoreOrder::getCreatedAt));
    }
}
