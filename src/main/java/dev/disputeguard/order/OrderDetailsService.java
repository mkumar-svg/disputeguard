package dev.disputeguard.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Loads an order with its shipment and chat, for the dashboard and for the AI agent's tools. */
@Service
public class OrderDetailsService {

    private final StoreOrderRepository orders;
    private final ShipmentRepository shipments;
    private final ChatMessageRepository messages;
    private final ObjectMapper mapper;

    public OrderDetailsService(StoreOrderRepository orders, ShipmentRepository shipments,
                               ChatMessageRepository messages, ObjectMapper mapper) {
        this.orders = orders;
        this.shipments = shipments;
        this.messages = messages;
        this.mapper = mapper;
    }

    public Optional<OrderDetails> byId(Long orderId) {
        return orderId == null ? Optional.empty() : orders.findById(orderId).map(this::of);
    }

    public OrderDetails of(StoreOrder o) {
        OrderDetails.ShipmentView shipment = shipments.findFirstByOrderId(o.getId())
                .map(s -> new OrderDetails.ShipmentView(s.getCarrier(), s.getTrackingNo(), s.getDeliveredAt(),
                        parse(s.getEvents())))
                .orElse(null);
        List<OrderDetails.MessageView> chat = messages.findByOrderIdOrderBySentAtAsc(o.getId()).stream()
                .map(m -> new OrderDetails.MessageView(m.getSender(), m.getText(), m.getSentAt()))
                .toList();
        return new OrderDetails(o.getId(), o.getPaypalOrderId(), o.getCaptureId(), o.getAmount(), o.getCurrency(),
                o.getDescription(), o.getStatus(), o.getScenario(), o.getBuyerEmail(), o.getShippingAddress(),
                o.getCreatedAt(), o.getCapturedAt(), shipment, chat);
    }

    private JsonNode parse(String json) {
        try {
            return json == null ? mapper.createArrayNode() : mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }
}
