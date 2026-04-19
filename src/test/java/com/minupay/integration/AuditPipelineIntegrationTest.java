package com.minupay.integration;

import com.minupay.audit.infrastructure.AuditLogDocument;
import com.minupay.common.money.Money;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("Audit 파이프라인 E2E (MySQL + Kafka + Mongo)")
class AuditPipelineIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("minupay")
            .withUsername("minupay")
            .withPassword("minupay");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
        reg.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        reg.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        reg.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        reg.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired private WalletService walletService;
    @Autowired private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("charge 이벤트가 Outbox → Kafka → MongoDB 감사로그까지 전달된다")
    void charge_event_reaches_audit_log() {
        Long userId = 424242L;
        walletService.createWallet(userId);

        String idemp = "e2e-" + UUID.randomUUID();
        walletService.charge(new ChargeCommand(
                userId, Money.of(5_000L), "e2e-ref", "CHARGE_REQUEST", idemp));

        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Query q = Query.query(Criteria.where("eventType").is("WalletCharged"));
                    AuditLogDocument doc = mongoTemplate.findOne(q, AuditLogDocument.class);
                    assertThat(doc).isNotNull();
                    assertThat(doc.getAggregateType()).isEqualTo("Wallet");
                    assertThat(doc.getPayload()).contains("5000");
                });
    }
}
