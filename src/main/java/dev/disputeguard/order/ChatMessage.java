package dev.disputeguard.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A message between buyer and seller about an order. */
@Entity
@Table(name = "messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** BUYER or SELLER. */
    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long orderId, String sender, String text, OffsetDateTime sentAt) {
        this.orderId = orderId;
        this.sender = sender;
        this.text = text;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public OffsetDateTime getSentAt() { return sentAt; }
}
