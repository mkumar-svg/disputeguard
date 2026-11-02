package dev.disputeguard.order;

/**
 * How the demo store "fulfilled" an order. The demo store does not ship real parcels, so after
 * capture it records a simulated shipment and buyer-seller chat that match one of these stories.
 * The scenario decides how strong the merchant's evidence is when a dispute arrives.
 */
public enum DemoScenario {
    /** Shipped with tracking, delivered days before the dispute, polite chat. Strong case: fight. */
    DELIVERED,
    /** Shipped with tracking, but the last scan is "in transit". Mixed case: offer a partial refund. */
    IN_TRANSIT,
    /** No shipment record and an unanswered buyer message. Weak case: accept. */
    NO_TRACKING;

    public static DemoScenario parse(String value) {
        if (value == null || value.isBlank()) {
            return DELIVERED;
        }
        return DemoScenario.valueOf(value.trim().toUpperCase());
    }
}
