package dev.disputeguard.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** An order with its shipment and buyer-seller chat. */
public record OrderDetails(
        Long id,
        String paypalOrderId,
        String captureId,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        String scenario,
        String buyerEmail,
        String shippingAddress,
        OffsetDateTime createdAt,
        OffsetDateTime capturedAt,
        ShipmentView shipment,
        List<MessageView> messages) {

    public record ShipmentView(String carrier, String trackingNo, OffsetDateTime deliveredAt, JsonNode events) {
    }

    public record MessageView(String sender, String text, OffsetDateTime sentAt) {
    }
}
