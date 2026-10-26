package com.claims.notification;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * The service as it runs, against a real Kafka broker started inside the test JVM (three partitions, like the compose file).
 * Retries are shortened so a failing email reaches the dead-letter topic in about a second.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=notification-it",
        "notifications.topic=" + KafkaFlowTestBase.TOPIC,
        "notifications.dead-letter-topic=" + KafkaFlowTestBase.DEAD_LETTER_TOPIC,
        "notifications.portal-url=" + TestEvents.PORTAL,
        "notifications.retry.max-retries=2",
        "notifications.retry.initial-delay-ms=50",
        "notifications.retry.max-delay-ms=200",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.kafka=WARN",
        "logging.level.state.change.logger=WARN",
        "logging.level.org.apache.zookeeper=WARN"})
@EmbeddedKafka(partitions = 3, topics = {KafkaFlowTestBase.TOPIC, KafkaFlowTestBase.DEAD_LETTER_TOPIC})
public abstract class KafkaFlowTestBase {
    public static final String TOPIC = "it.claim-status-changed";
    public static final String DEAD_LETTER_TOPIC = "it.claim-status-changed.dlt";

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;

    private Consumer<String, String> deadLetterConsumer;
    private final List<ConsumerRecord<String, String>> deadLetters = Collections.synchronizedList(new ArrayList<>());

    /**
     * Reads the dead-letter topic from its start. The partitions are assigned directly instead of joining a consumer group: a group join
     * waits for a rebalance and, on a busy machine, made single tests take a minute or more.
     */
    @BeforeEach
    void listenToTheDeadLetterTopic() {
        var props = KafkaTestUtils.consumerProps("dlt-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        deadLetterConsumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        List<TopicPartition> partitions = deadLetterConsumer.partitionsFor(DEAD_LETTER_TOPIC).stream()
                .map(info -> new TopicPartition(info.topic(), info.partition())).toList();
        deadLetterConsumer.assign(partitions);
        deadLetterConsumer.seekToBeginning(partitions);
        deadLetters.clear();
    }

    @AfterEach
    void stopListening() {
        deadLetterConsumer.close();
    }

    /** Publishes like the backend does: the claim id is the key, so one claim's events stay in order. */
    protected void publish(String key, String json) throws Exception {
        kafka.send(TOPIC, key, json).get(10, TimeUnit.SECONDS);
    }

    /** Waits until the dead-letter topic holds a record whose payload matches. Records are kept, so earlier ones can be asked about again. */
    protected ConsumerRecord<String, String> awaitDeadLetter(Predicate<String> payload) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> {
            deadLetterConsumer.poll(Duration.ofMillis(200)).forEach(deadLetters::add);
            return findDeadLetter(payload) != null;
        });
        return findDeadLetter(payload);
    }

    /** True if no matching record shows up on the dead-letter topic for a short while. */
    protected boolean noDeadLetterWithin(Duration wait, Predicate<String> payload) throws InterruptedException {
        long end = System.nanoTime() + wait.toNanos();
        while (System.nanoTime() < end) {
            deadLetterConsumer.poll(Duration.ofMillis(200)).forEach(deadLetters::add);
            if (findDeadLetter(payload) != null) {
                return false;
            }
        }
        return true;
    }

    private ConsumerRecord<String, String> findDeadLetter(Predicate<String> payload) {
        synchronized (deadLetters) {
            return deadLetters.stream().filter(r -> r.value() != null && payload.test(r.value())).findFirst().orElse(null);
        }
    }
}
