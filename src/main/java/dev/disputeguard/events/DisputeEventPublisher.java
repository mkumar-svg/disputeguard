package dev.disputeguard.events;

/**
 * Hands a verified PayPal dispute webhook to the processing pipeline.
 * Kafka locally (the full event-driven setup); in-process on hosts without Kafka (the hosted demo).
 */
public interface DisputeEventPublisher {

    /**
     * @param topic     logical event stream (dispute.created / dispute.updated)
     * @param disputeId the PayPal dispute id, used as the message key so one dispute's events stay ordered
     * @param rawBody   the webhook body as PayPal sent it
     */
    void publish(String topic, String disputeId, String rawBody) throws Exception;
}
