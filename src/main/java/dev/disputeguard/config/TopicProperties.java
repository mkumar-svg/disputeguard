package dev.disputeguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka topic names, bound from {@code disputeguard.topics.*}. */
@ConfigurationProperties(prefix = "disputeguard.topics")
public record TopicProperties(String disputeCreated, String disputeUpdated) {
}
