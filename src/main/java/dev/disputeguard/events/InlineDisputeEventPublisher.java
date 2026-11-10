package dev.disputeguard.events;

import dev.disputeguard.dispute.DisputeEventProcessor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * For hosts without Kafka (EVENTS_MODE=inline): process the event on a single background thread, so
 * PayPal still gets a fast 200 and one dispute's events are handled in arrival order.
 */
@Component
@ConditionalOnProperty(name = "disputeguard.events.mode", havingValue = "inline")
public class InlineDisputeEventPublisher implements DisputeEventPublisher {

    private final DisputeEventProcessor processor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dispute-events");
        t.setDaemon(true);
        return t;
    });

    public InlineDisputeEventPublisher(DisputeEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void publish(String topic, String disputeId, String rawBody) {
        worker.submit(() -> processor.process(disputeId, topic + " (inline)"));
    }
}
