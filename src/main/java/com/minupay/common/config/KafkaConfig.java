package com.minupay.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String TOPIC_WALLET_CHARGED = "wallet.charged";
    public static final String TOPIC_WALLET_DEDUCTED = "wallet.deducted";
    public static final String TOPIC_WALLET_REFUNDED = "wallet.refunded";
    public static final String TOPIC_PAYMENT_APPROVED = "payment.approved";
    public static final String TOPIC_PAYMENT_FAILED = "payment.failed";
    public static final String TOPIC_PAYMENT_CANCELLED = "payment.cancelled";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean public NewTopic walletChargedTopic() { return TopicBuilder.name(TOPIC_WALLET_CHARGED).partitions(3).replicas(1).build(); }
    @Bean public NewTopic walletDeductedTopic() { return TopicBuilder.name(TOPIC_WALLET_DEDUCTED).partitions(3).replicas(1).build(); }
    @Bean public NewTopic walletRefundedTopic() { return TopicBuilder.name(TOPIC_WALLET_REFUNDED).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentApprovedTopic() { return TopicBuilder.name(TOPIC_PAYMENT_APPROVED).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentFailedTopic() { return TopicBuilder.name(TOPIC_PAYMENT_FAILED).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentCancelledTopic() { return TopicBuilder.name(TOPIC_PAYMENT_CANCELLED).partitions(3).replicas(1).build(); }
}
