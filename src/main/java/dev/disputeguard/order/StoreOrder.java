package dev.disputeguard.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** An order placed in the demo store and paid through PayPal Checkout (Orders API v2). */
@Entity
@Table(name = "orders")
public class StoreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paypal_order_id", unique = true)
    private String paypalOrderId;

    @Column(name = "capture_id", unique = true)
    private String captureId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String description;

    @Column(name = "buyer_email")
    private String buyerEmail;

    /** CREATED, COMPLETED. */
    private String status;

    /** Demo fulfilment story: DELIVERED, IN_TRANSIT or NO_TRACKING (see DemoScenario). */
    private String scenario;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    protected StoreOrder() {
    }

    public StoreOrder(String paypalOrderId, BigDecimal amount, String currency, String description, String scenario) {
        this.paypalOrderId = paypalOrderId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.scenario = scenario;
        this.status = "CREATED";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getPaypalOrderId() { return paypalOrderId; }
    public String getCaptureId() { return captureId; }
    public void setCaptureId(String captureId) { this.captureId = captureId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }
}
