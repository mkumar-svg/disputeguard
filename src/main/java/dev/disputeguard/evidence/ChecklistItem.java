package dev.disputeguard.evidence;

/**
 * One piece of evidence PayPal expects for a dispute reason, and whether the merchant has it.
 *
 * @param key    stable id, e.g. {@code TRACKING}
 * @param label  what the dashboard shows
 * @param status PRESENT, WEAK or MISSING
 * @param detail the fact found (or what is missing), in plain words
 * @param source where the fact came from: order, shipment, chat, dispute
 * @param weight how much this item counts towards the win chance (weights of one reason sum to 1)
 */
public record ChecklistItem(String key, String label, Status status, String detail, String source, double weight) {

    public enum Status {
        PRESENT(1.0), WEAK(0.5), MISSING(0.0);

        public final double value;

        Status(double value) {
            this.value = value;
        }
    }
}
