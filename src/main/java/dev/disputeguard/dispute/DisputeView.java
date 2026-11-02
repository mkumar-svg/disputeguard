package dev.disputeguard.dispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** What the dashboard sees for a dispute (no raw PayPal payload). */
public record DisputeView(
        Long id,
        String paypalDisputeId,
        String reason,
        String status,
        BigDecimal amount,
        String currency,
        OffsetDateTime sellerResponseDue,
        OffsetDateTime openedAt,
        String transactionId,
        Long orderId,
        String buyerMessage,
        OffsetDateTime updatedAt) {

    public static DisputeView from(Dispute d) {
        return new DisputeView(d.getId(), d.getPaypalDisputeId(), d.getReason(), d.getStatus(),
                d.getAmount(), d.getCurrency(), d.getSellerResponseDue(), d.getDisputeCreatedAt(),
                d.getTransactionId(), d.getOrderId(), d.getBuyerMessage(), d.getUpdatedAt());
    }
}
