package dev.disputeguard.paypal;

/** The PAYPAL-* transmission headers PayPal sends with every webhook delivery. */
public record WebhookHeaders(
        String authAlgo,
        String certUrl,
        String transmissionId,
        String transmissionSig,
        String transmissionTime) {
}
