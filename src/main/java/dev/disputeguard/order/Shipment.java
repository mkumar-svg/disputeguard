package dev.disputeguard.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Shipping record for an order. {@code events} is a JSON array of tracking scans. */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    private String carrier;

    @Column(name = "tracking_no")
    private String trackingNo;

    private String events;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    protected Shipment() {
    }

    public Shipment(Long orderId, String carrier, String trackingNo, String events, OffsetDateTime deliveredAt) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNo = trackingNo;
        this.events = events;
        this.deliveredAt = deliveredAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getCarrier() { return carrier; }
    public String getTrackingNo() { return trackingNo; }
    public String getEvents() { return events; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
}
