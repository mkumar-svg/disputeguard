package dev.disputeguard.dispute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A PayPal dispute, kept in sync from webhooks and the Disputes API. */
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paypal_dispute_id", nullable = false, unique = true)
    private String paypalDisputeId;

    private String reason;
    private String status;

    /** Amount the buyer is claiming. */
    private BigDecimal amount;
    private String currency;

    /** Amount of the original payment. */
    @Column(name = "gross_amount")
    private BigDecimal grossAmount;

    @Column(name = "seller_response_due")
    private OffsetDateTime sellerResponseDue;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "buyer_transaction_id")
    private String buyerTransactionId;

    @Column(name = "dispute_created_at")
    private OffsetDateTime disputeCreatedAt;

    @Column(name = "buyer_message")
    private String buyerMessage;

    /** The demo-store order this dispute is about, once linked. */
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Dispute() {
    }

    public Dispute(String paypalDisputeId) {
        this.paypalDisputeId = paypalDisputeId;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getPaypalDisputeId() { return paypalDisputeId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public OffsetDateTime getSellerResponseDue() { return sellerResponseDue; }
    public void setSellerResponseDue(OffsetDateTime sellerResponseDue) { this.sellerResponseDue = sellerResponseDue; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getBuyerTransactionId() { return buyerTransactionId; }
    public void setBuyerTransactionId(String buyerTransactionId) { this.buyerTransactionId = buyerTransactionId; }
    public OffsetDateTime getDisputeCreatedAt() { return disputeCreatedAt; }
    public void setDisputeCreatedAt(OffsetDateTime disputeCreatedAt) { this.disputeCreatedAt = disputeCreatedAt; }
    public String getBuyerMessage() { return buyerMessage; }
    public void setBuyerMessage(String buyerMessage) { this.buyerMessage = buyerMessage; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
