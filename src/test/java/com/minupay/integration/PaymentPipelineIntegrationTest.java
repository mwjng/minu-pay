package com.minupay.integration;

import com.minupay.audit.infrastructure.AuditLogDocument;
import com.minupay.common.money.Money;
import com.minupay.payment.application.PaymentFacade;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.domain.PaymentStatus;
import com.minupay.payment.infrastructure.pg.PgClient;
import com.minupay.payment.infrastructure.pg.PgResult;
import com.minupay.settlement.domain.SettlementItem;
import com.minupay.settlement.domain.SettlementItemRepository;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("Payment 파이프라인 E2E (PG stub + MySQL + Kafka + Mongo)")
class PaymentPipelineIntegrationTest {

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

    @TestConfiguration
    static class StubPgConfig {
        @Bean @Primary
        PgClient stubPgClient() {
            return new PgClient() {
                @Override public PgResult approve(com.minupay.payment.infrastructure.pg.PgApproveRequest req) {
                    return PgResult.success("pg-tx-" + UUID.randomUUID(), null);
                }
                @Override public PgResult cancel(String pgTxId, String reason) {
                    return PgResult.success(pgTxId, null);
                }
            };
        }
    }

    @Autowired private WalletService walletService;
    @Autowired private PaymentFacade paymentFacade;
    @Autowired private SettlementItemRepository settlementItemRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("결제 성공 시 PaymentApproved 이벤트가 Audit + Settlement까지 전달된다")
    void approved_payment_reaches_audit_and_settlement() {
        Long userId = 7001L;
        String merchantId = "merchant-e2e-" + UUID.randomUUID();
        walletService.createWallet(userId);
        walletService.charge(new ChargeCommand(
                userId, Money.of(20_000L), "seed", "INIT", "seed-" + UUID.randomUUID()));

        PaymentCommand command = new PaymentCommand(
                userId, merchantId, 3_000L,
                "pay-" + UUID.randomUUID(),
                "toss-key-" + UUID.randomUUID()
        );

        PaymentInfo result = paymentFacade.request(command);
        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);

        await().atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Query q = Query.query(Criteria.where("eventType").is("PaymentApproved")
                            .and("aggregateId").is(result.paymentId()));
                    AuditLogDocument doc = mongoTemplate.findOne(q, AuditLogDocument.class);
                    assertThat(doc).isNotNull();
                    assertThat(doc.getAggregateType()).isEqualTo("Payment");

                    Optional<SettlementItem> item = settlementItemRepository.findByPaymentId(result.paymentId());
                    assertThat(item).isPresent();
                    assertThat(item.get().getMerchantId()).isEqualTo(merchantId);
                    assertThat(item.get().getGrossAmount().toLong()).isEqualTo(3_000L);
                });
    }
}
