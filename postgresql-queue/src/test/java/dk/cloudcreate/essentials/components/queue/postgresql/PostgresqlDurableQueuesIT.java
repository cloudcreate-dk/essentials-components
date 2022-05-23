package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.common.transaction.GenericHandleAwareUnitOfWorkFactory;
import dk.cloudcreate.essentials.components.queue.*;
import dk.cloudcreate.essentials.components.queue.postgresql.test_data.*;
import org.awaitility.Awaitility;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresqlDurableQueuesIT {
    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("queue-db")
            .withUsername("test-user")
            .withPassword("secret-password");

    private GenericHandleAwareUnitOfWorkFactory<GenericHandleAwareUnitOfWorkFactory.GenericHandleAwareUnitOfWork> unitOfWorkFactory;
    private PostgresqlDurableQueues                                                                               durableQueues;

    @BeforeEach
    void setup() {
        unitOfWorkFactory = new GenericHandleAwareUnitOfWorkFactory<>(Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                                                                                  postgreSQLContainer.getUsername(),
                                                                                  postgreSQLContainer.getPassword())) {

            @Override
            protected GenericHandleAwareUnitOfWork createNewUnitOfWorkInstance(GenericHandleAwareUnitOfWorkFactory<GenericHandleAwareUnitOfWork> unitOfWorkFactory) {
                return new GenericHandleAwareUnitOfWork(unitOfWorkFactory);
            }
        };
        durableQueues = new PostgresqlDurableQueues(unitOfWorkFactory);
        durableQueues.start();
    }

    @AfterEach
    void cleanup() {
        if (durableQueues != null) {
            durableQueues.stop();
        }
    }

    @Test
    void test_simple_enqueueing_and_afterwards_querying_queued_messages() {
        // Given
        var queueName = QueueName.of("TestQueue");

        // When
        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var idMsg1   = durableQueues.queueMessage(queueName, message1);
        var message2 = new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2);

        var idMsg2   = durableQueues.queueMessage(queueName, message2);
        var message3 = new OrderEvent.OrderAccepted(OrderId.random());
        var idMsg3   = durableQueues.queueMessage(queueName, message3);

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        var queuedMessages = durableQueues.getQueuedMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(queuedMessages.size()).isEqualTo(3);

        assertThat(durableQueues.getQueuedMessage(idMsg1).get()).isEqualTo(queuedMessages.get(0));
        assertThat(queuedMessages.get(0).payload).usingRecursiveComparison().isEqualTo(message1);
        assertThat(queuedMessages.get(0).id).isEqualTo(idMsg1);
        assertThat((CharSequence) queuedMessages.get(0).queueName).isEqualTo(queueName);
        assertThat(queuedMessages.get(0).addedTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).nextDeliveryTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(0).isDeadLetterMessage).isFalse();
        assertThat(queuedMessages.get(0).lastDeliveryError).isEqualTo(null);
        assertThat(queuedMessages.get(0).redeliveryAttempts).isEqualTo(0);
        assertThat(queuedMessages.get(0).totalDeliveryAttempts).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg2).get()).isEqualTo(queuedMessages.get(1));
        assertThat(queuedMessages.get(1).payload).usingRecursiveComparison().isEqualTo(message2);
        assertThat(queuedMessages.get(1).id).isEqualTo(idMsg2);
        assertThat((CharSequence) queuedMessages.get(1).queueName).isEqualTo(queueName);
        assertThat(queuedMessages.get(1).addedTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).nextDeliveryTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(1).isDeadLetterMessage).isFalse();
        assertThat(queuedMessages.get(1).lastDeliveryError).isEqualTo(null);
        assertThat(queuedMessages.get(1).redeliveryAttempts).isEqualTo(0);
        assertThat(queuedMessages.get(1).totalDeliveryAttempts).isEqualTo(0);

        assertThat(durableQueues.getQueuedMessage(idMsg3).get()).isEqualTo(queuedMessages.get(2));
        assertThat(queuedMessages.get(2).payload).usingRecursiveComparison().isEqualTo(message3);
        assertThat(queuedMessages.get(2).id).isEqualTo(idMsg3);
        assertThat((CharSequence) queuedMessages.get(2).queueName).isEqualTo(queueName);
        assertThat(queuedMessages.get(2).addedTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).nextDeliveryTimestamp).isBefore(OffsetDateTime.now(Clock.systemUTC()));
        assertThat(queuedMessages.get(2).isDeadLetterMessage).isFalse();
        assertThat(queuedMessages.get(2).lastDeliveryError).isEqualTo(null);
        assertThat(queuedMessages.get(2).redeliveryAttempts).isEqualTo(0);
        assertThat(queuedMessages.get(2).totalDeliveryAttempts).isEqualTo(0);

        // And When
        var numberOfDeletedMessages = durableQueues.purgeQueue(queueName);

        // Then
        assertThat(numberOfDeletedMessages).isEqualTo(3);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
    }

    @Test
    void verify_queued_messages_are_dequeued_in_order() {
        // Given
        var queueName = QueueName.of("TestQueue");
        durableQueues.purgeQueue(queueName);

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        var idMsg1   = durableQueues.queueMessage(queueName, message1);
        var message2 = new OrderEvent.ProductAddedToOrder(OrderId.random(), ProductId.random(), 2);

        var idMsg2   = durableQueues.queueMessage(queueName, message2);
        var message3 = new OrderEvent.OrderAccepted(OrderId.random());
        var idMsg3   = durableQueues.queueMessage(queueName, message3);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(3);
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      QueueRedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5),
                                                      1,
                                                      recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(3));
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message2);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message3);

        consumer.cancel();
    }

    @Test
    void verify_a_message_queues_as_a_dead_letter_message_is_marked_as_such_and_will_not_be_delivered_to_the_consumer() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 1234);
        durableQueues.queueMessageAsDeadLetterMessage(queueName, message1, new RuntimeException("On purpose"));

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        var deadLetterMessages = durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20);
        assertThat(deadLetterMessages.size()).isEqualTo(1);

        // When
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler();
        var consumer                     = durableQueues.consumeFromQueue(queueName, QueueRedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler);

        // When
        Awaitility.await()
                  .atMost(Duration.ofSeconds(2))
                  .until(() -> recordingQueueMessageHandler.messages.size() == 0);

        consumer.cancel();
    }

    @Test
    void verify_failed_messages_are_redelivered() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1 = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 12345);
        durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);

        AtomicInteger deliveryCountForMessage1 = new AtomicInteger();
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            var count = deliveryCountForMessage1.incrementAndGet();
            if (count <= 3) {
                throw new RuntimeException("Thrown on purpose. Delivery count: " + deliveryCountForMessage1);
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName, QueueRedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler);

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(4)); // 3 redeliveries and 1 final delivery
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(3)).usingRecursiveComparison().isEqualTo(message1);

        consumer.cancel();
    }

    @Test
    void verify_a_message_that_failed_too_many_times_is_marked_as_dead_letter_message_AND_the_message_can_be_resurrected() {
        // Given
        var queueName = QueueName.of("TestQueue");

        var message1   = new OrderEvent.OrderAdded(OrderId.random(), CustomerId.random(), 123456);
        var message1Id = durableQueues.queueMessage(queueName, message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        AtomicInteger deliveryCountForMessage1 = new AtomicInteger();
        var recordingQueueMessageHandler = new RecordingQueuedMessageHandler(msg -> {
            var count = deliveryCountForMessage1.incrementAndGet();
            if (count <= 6) { // 1 initial delivery and 5 redeliveries - after that the message will be marked as a dead letter message. On resurrection that message will be delivered without error
                throw new RuntimeException("Thrown on purpose. Delivery count: " + deliveryCountForMessage1);
            }
        });

        // When
        var consumer = durableQueues.consumeFromQueue(queueName,
                                                      QueueRedeliveryPolicy.fixedBackoff(Duration.ofMillis(200), 5), 1, recordingQueueMessageHandler
                                                     );

        // Then
        Awaitility.waitAtMost(Duration.ofSeconds(4))
                  .untilAsserted(() -> assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(6)); // 6 in total (1 initial delivery and 5 redeliveries)
        assertThat(recordingQueueMessageHandler.messages.get(0)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(1)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(2)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(3)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(4)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(recordingQueueMessageHandler.messages.get(5)).usingRecursiveComparison().isEqualTo(message1);

        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0); // Dead letter messages is not counted
        var deadLetterMessage = durableQueues.getDeadLetterMessage(message1Id);
        assertThat(deadLetterMessage).isPresent();
        assertThat(deadLetterMessage.get().payload).usingRecursiveComparison().isEqualTo(message1);
        assertThat(deadLetterMessage.get()).usingRecursiveComparison().isEqualTo(durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20).get(0));

        // And When
        assertThat(durableQueues.resurrectDeadLetterMessage(message1Id, Duration.ofMillis(1000))).isTrue();

        // Then
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofSeconds(2))
                  .untilAsserted(() -> {
                      assertThat(recordingQueueMessageHandler.messages.size()).isEqualTo(7); // 7 in total (1 initial delivery and 5 redeliveries and 1 final delivery for the resurrected message)
                  });
        assertThat(recordingQueueMessageHandler.messages.get(6)).usingRecursiveComparison().isEqualTo(message1);
        assertThat(durableQueues.getTotalMessagesQueuedFor(queueName)).isEqualTo(0);
        assertThat(durableQueues.getDeadLetterMessages(queueName, DurableQueues.QueueingSortOrder.ASC, 0, 20)).isEmpty();

        consumer.cancel();
    }

    private static class RecordingQueuedMessageHandler implements QueuedMessageHandler {
        Consumer<Object> functionLogic;
        List<Object>     messages = new ArrayList<>();

        RecordingQueuedMessageHandler() {
        }

        RecordingQueuedMessageHandler(Consumer<Object> functionLogic) {
            this.functionLogic = functionLogic;
        }

        @Override
        public void handle(QueuedMessage message) {
            messages.add(message.payload);
            if (functionLogic != null) {
                functionLogic.accept(message.payload);
            }
        }
    }
}