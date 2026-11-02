package dev.disputeguard.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the simulated shipment and chat for a demo-store order (see {@link DemoScenario}).
 * The story is dated as if the order was placed seven days ago, so a dispute opened today comes
 * after the delivery, just like a real "item not received" claim against a delivered parcel.
 * Idempotent: an order that already has chat or a shipment is left alone.
 */
@Component
public class DemoFulfillmentSeeder {

    private final ShipmentRepository shipments;
    private final ChatMessageRepository messages;
    private final ObjectMapper mapper;

    public DemoFulfillmentSeeder(ShipmentRepository shipments, ChatMessageRepository messages, ObjectMapper mapper) {
        this.shipments = shipments;
        this.messages = messages;
        this.mapper = mapper;
    }

    @Transactional
    public void seed(StoreOrder order) {
        if (shipments.findFirstByOrderId(order.getId()).isPresent() || messages.existsByOrderId(order.getId())) {
            return;
        }
        DemoScenario scenario = DemoScenario.parse(order.getScenario());
        OffsetDateTime placed = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7).withHour(10).withMinute(15);
        String tracking = "9400111899" + String.format("%012d", 560000000000L + order.getId());
        String item = order.getDescription() == null ? "your order" : order.getDescription();

        switch (scenario) {
            case DELIVERED -> {
                ArrayNode events = mapper.createArrayNode();
                event(events, placed.plusHours(3), "LABEL_CREATED", "San Jose, CA", "Shipping label created");
                event(events, placed.plusDays(1), "PICKED_UP", "San Jose, CA", "Picked up by USPS");
                event(events, placed.plusDays(2), "IN_TRANSIT", "Oakland, CA", "Arrived at USPS regional facility");
                event(events, placed.plusDays(3), "OUT_FOR_DELIVERY", "San Jose, CA", "Out for delivery");
                OffsetDateTime delivered = placed.plusDays(3).plusHours(5);
                event(events, delivered, "DELIVERED", "San Jose, CA", "Delivered, front door/porch");
                shipments.save(new Shipment(order.getId(), "USPS", tracking, events.toString(), delivered));

                chat(order, "BUYER", placed.plusHours(2), "Hi, when will " + item + " ship?");
                chat(order, "SELLER", placed.plusDays(1).plusHours(1),
                        "It shipped today with USPS, tracking number " + tracking + ".");
                chat(order, "BUYER", placed.plusDays(1).plusHours(2), "Great, thanks!");
            }
            case IN_TRANSIT -> {
                ArrayNode events = mapper.createArrayNode();
                event(events, placed.plusHours(3), "LABEL_CREATED", "San Jose, CA", "Shipping label created");
                event(events, placed.plusDays(1), "PICKED_UP", "San Jose, CA", "Picked up by USPS");
                event(events, placed.plusDays(2), "IN_TRANSIT", "Oakland, CA", "In transit, arriving late");
                shipments.save(new Shipment(order.getId(), "USPS", tracking, events.toString(), null));

                chat(order, "BUYER", placed.plusDays(5), "My package still hasn't arrived. Where is it?");
                chat(order, "SELLER", placed.plusDays(5).plusHours(4),
                        "Sorry about the delay. USPS shows it in transit, tracking " + tracking + ".");
            }
            case NO_TRACKING -> chat(order, "BUYER", placed.plusDays(4), "Has my order shipped? I haven't heard anything.");
        }
    }

    private void event(ArrayNode events, OffsetDateTime time, String status, String location, String description) {
        events.addObject()
                .put("time", time.toString())
                .put("status", status)
                .put("location", location)
                .put("description", description);
    }

    private void chat(StoreOrder order, String sender, OffsetDateTime at, String text) {
        messages.save(new ChatMessage(order.getId(), sender, text, at));
    }
}
