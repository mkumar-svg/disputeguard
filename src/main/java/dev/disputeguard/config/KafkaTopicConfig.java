package dev.disputeguard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the dispute topics on startup if they do not exist yet. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic disputeCreatedTopic(TopicProperties topics) {
        return TopicBuilder.name(topics.disputeCreated()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic disputeUpdatedTopic(TopicProperties topics) {
        return TopicBuilder.name(topics.disputeUpdated()).partitions(3).replicas(1).build();
    }
}
