package dev.disputeguard.evidence;

import dev.disputeguard.dispute.Dispute;
import dev.disputeguard.evidence.ChecklistItem.Status;
import dev.disputeguard.order.OrderDetails;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rule-based evidence checklist per dispute reason, and the explainable base win chance it yields.
 * The AI agent may adjust the result a little, but the checklist is the backbone of every score.
 */
@Component
public class EvidenceChecklist {

    static final String ITEM_NOT_RECEIVED = "MERCHANDISE_OR_SERVICE_NOT_RECEIVED";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    public List<ChecklistItem> build(Dispute dispute, OrderDetails order) {
        return ITEM_NOT_RECEIVED.equals(dispute.getReason())
                ? itemNotReceived(dispute, order)
                : generic(order);
    }

    /** Base win chance: 10% floor plus up to 80% from the weighted checklist. */
    public double baseProbability(List<ChecklistItem> items) {
        double total = items.stream().mapToDouble(ChecklistItem::weight).sum();
        double score = items.stream().mapToDouble(i -> i.weight() * i.status().value).sum();
        double p = 0.10 + 0.80 * (total == 0 ? 0 : score / total);
        return Math.round(p * 1000) / 1000.0;
    }

    private List<ChecklistItem> itemNotReceived(Dispute dispute, OrderDetails order) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(orderItem(order, 0.10));

        OrderDetails.ShipmentView ship = order == null ? null : order.shipment();
        boolean hasTracking = ship != null && ship.trackingNo() != null;
        items.add(new ChecklistItem("TRACKING", "Shipped with tracking",
                hasTracking ? Status.PRESENT : Status.MISSING,
                hasTracking ? ship.carrier() + " tracking " + ship.trackingNo() : "No shipment or tracking number on file",
                "shipment", 0.25));

        boolean delivered = ship != null && ship.deliveredAt() != null;
        items.add(new ChecklistItem("DELIVERED", "Carrier confirms delivery",
                delivered ? Status.PRESENT : hasTracking ? Status.WEAK : Status.MISSING,
                delivered ? "Delivered on " + DAY.format(ship.deliveredAt())
                        : hasTracking ? "Tracking shows the parcel in transit, not delivered" : "No delivery scan",
                "shipment", 0.30));

        OffsetDateTime opened = dispute.getDisputeCreatedAt();
        boolean before = delivered && (opened == null || ship.deliveredAt().isBefore(opened));
        items.add(new ChecklistItem("DELIVERED_BEFORE_DISPUTE", "Delivered before the dispute was opened",
                before ? Status.PRESENT : Status.MISSING,
                before ? "Delivery came before the buyer opened the dispute"
                        + (opened == null ? "" : " on " + DAY.format(opened))
                        : "No delivery before the dispute date",
                "shipment", 0.15));

        items.add(communicationItem(order, 0.20));
        return items;
    }

    private List<ChecklistItem> generic(OrderDetails order) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(orderItem(order, 0.30));
        OrderDetails.ShipmentView ship = order == null ? null : order.shipment();
        items.add(new ChecklistItem("TRACKING", "Shipped with tracking",
                ship != null && ship.trackingNo() != null ? Status.PRESENT : Status.MISSING,
                ship != null && ship.trackingNo() != null ? ship.carrier() + " tracking " + ship.trackingNo()
                        : "No tracking on file",
                "shipment", 0.30));
        items.add(communicationItem(order, 0.40));
        return items;
    }

    private ChecklistItem orderItem(OrderDetails order, double weight) {
        return order == null
                ? new ChecklistItem("ORDER", "Order record found", Status.MISSING,
                        "No order in the store matches this transaction", "order", weight)
                : new ChecklistItem("ORDER", "Order record found", Status.PRESENT,
                        "Order " + order.paypalOrderId() + ", " + order.amount() + " " + order.currency()
                                + (order.description() == null ? "" : ", " + order.description()),
                        "order", weight);
    }

    private ChecklistItem communicationItem(OrderDetails order, double weight) {
        if (order == null || order.messages().isEmpty()) {
            return new ChecklistItem("COMMUNICATION", "Seller kept the buyer informed", Status.MISSING,
                    "No buyer-seller messages on file", "chat", weight);
        }
        boolean sellerReplied = order.messages().stream().anyMatch(m -> "SELLER".equals(m.sender()));
        String tracking = order.shipment() == null ? null : order.shipment().trackingNo();
        // Strong only when the seller shared tracking AND the buyer replied afterwards (acknowledged it).
        OrderDetails.MessageView shared = tracking == null ? null : order.messages().stream()
                .filter(m -> "SELLER".equals(m.sender()) && m.text().contains(tracking))
                .findFirst().orElse(null);
        boolean acknowledged = shared != null && order.messages().stream()
                .anyMatch(m -> "BUYER".equals(m.sender()) && m.sentAt().isAfter(shared.sentAt()));
        if (acknowledged) {
            return new ChecklistItem("COMMUNICATION", "Seller kept the buyer informed", Status.PRESENT,
                    "Seller shared tracking " + tracking + " and the buyer acknowledged it", "chat", weight);
        }
        return sellerReplied
                ? new ChecklistItem("COMMUNICATION", "Seller kept the buyer informed", Status.WEAK,
                        "Seller replied, but without proof of delivery", "chat", weight)
                : new ChecklistItem("COMMUNICATION", "Seller kept the buyer informed", Status.MISSING,
                        "Buyer's message went unanswered", "chat", weight);
    }
}
