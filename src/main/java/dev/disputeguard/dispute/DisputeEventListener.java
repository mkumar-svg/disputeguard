package dev.disputeguard.dispute;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Enrichment worker (Kafka mode): consumes dispute events and hands them to the processor. */
@Component
@ConditionalOnProperty(name = "disputeguard.events.mode", havingValue = "kafka", matchIfMissing = true)
public class DisputeEventListener {

    private final DisputeEventProcessor processor;

    public DisputeEventListener(DisputeEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = {"${disputeguard.topics.dispute-created}", "${disputeguard.topics.dispute-updated}"})
    public void onDisputeEvent(ConsumerRecord<String, String> record) {
        processor.process(record.key(), record.topic());
    }
}
