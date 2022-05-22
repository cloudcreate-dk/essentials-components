package dk.cloudcreate.essentials.components.queue;

import dk.cloudcreate.essentials.components.common.Lifecycle;

import java.time.Duration;
import java.util.*;

/**
 * Durable Queue concept that supports queuing a message on to a Queue. Each message is associated with a unique {@link QueueEntryId}<br>
 * Each Queue is uniquely identified by its {@link QueueName}<br>
 * Queued messages can, per Queue, asynchronously be consumed by a {@link QueuedMessageHandler}, by registering it as a {@link DurableQueueConsumer} using
 * {@link #consumeFromQueue(QueueName, QueueRedeliveryPolicy, int, QueuedMessageHandler)}<br>
 * <br>
 * The {@link DurableQueues} supports delayed message delivery as well as Dead Letter Messages, which are messages that have been marked as a Dead Letter Messages (due to an error processing the message).<br>
 * Dead Letter Messages won't be delivered to a {@link DurableQueueConsumer}, unless you call {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}<br>
 * <br>
 * The {@link DurableQueueConsumer} supports retrying failed messages, according to the specified {@link QueueRedeliveryPolicy}, and ultimately marking a repeatedly failing message
 * as a Dead Letter Message.<br>
 * The {@link QueueRedeliveryPolicy} supports fixed, linear and exponential backoff strategies.
 *
 * @see dk.cloudcreate.essentials.components.queue.postgresql.PostgresqlDurableQueues
 */
public interface DurableQueues extends Lifecycle {
    /**
     * The sorting order for the {@link QueuedMessage#id}
     */
    enum QueueingSortOrder {
        /**
         * Ascending order
         */
        ASC,
        /**
         * Descending order
         */
        DESC
    }

    /**
     * Get a queued message that's marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getDeadLetterMessage(QueueEntryId queueEntryId);

    /**
     * Get a queued message that's is NOT marked as a {@link QueuedMessage#isDeadLetterMessage}
     *
     * @param queueEntryId the messages unique queue entry id
     * @return the message wrapped in an {@link Optional} if the message exists and NOT a {@link QueuedMessage#isDeadLetterMessage}, otherwise {@link Optional#empty()}
     */
    Optional<QueuedMessage> getQueuedMessage(QueueEntryId queueEntryId);


    /**
     * Start an asynchronous message consumer.<br>
     * Note: There can only be one {@link DurableQueueConsumer} per {@link QueueName}
     *
     * @param queueName           the name of the queue that the consumer will be listening for queued messages ready to be delivered to the {@link QueuedMessageHandler} provided
     * @param redeliveryPolicy    the redelivery policy in case the handling of a message fails
     * @param parallelConsumers   the number of parallel consumers (if number > 1 then you will effectively have competing consumers)
     * @param queueMessageHandler the message handler that will receive {@link QueuedMessage}'s
     * @return the queue consumer
     */
    DurableQueueConsumer consumeFromQueue(QueueName queueName,
                                          QueueRedeliveryPolicy redeliveryPolicy,
                                          int parallelConsumers,
                                          QueuedMessageHandler queueMessageHandler);

    /**
     * Queue a message for asynchronous delivery without delay to a {@link DurableQueueConsumer}
     *
     * @param queueName the name of the Queue the message is added to
     * @param payload   the message payload
     * @return the unique entry id for the message queued
     */
    default QueueEntryId queueMessage(QueueName queueName, Object payload) {
        return queueMessage(queueName,
                            payload,
                            Optional.empty(),
                            Optional.empty());
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}
     *
     * @param queueName     the name of the Queue the message is added to
     * @param payload       the message payload
     * @param deliveryDelay Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     */
    default QueueEntryId queueMessage(QueueName queueName, Object payload, Optional<Duration> deliveryDelay) {
        return queueMessage(queueName,
                            payload,
                            Optional.empty(),
                            deliveryDelay);
    }

    /**
     * Queue a message for asynchronous delivery optional delay to a {@link DurableQueueConsumer}
     *
     * @param queueName        the name of the Queue the message is added to
     * @param payload          the message payload
     * @param causeOfEnqueuing the optional reason for the message being queued
     * @param deliveryDelay    Optional delay for the first delivery of the message to the {@link DurableQueueConsumer}
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessage(QueueName queueName, Object payload, Optional<Exception> causeOfEnqueuing, Optional<Duration> deliveryDelay);

    /**
     * Queue the message directly as a Dead Letter Message. Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer}<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param queueName    the name of the Queue the message is added to
     * @param payload      the message payload
     * @param causeOfError the reason for the message being queued directly as a Dead Letter Message
     * @return the unique entry id for the message queued
     */
    QueueEntryId queueMessageAsDeadLetterMessage(QueueName queueName, Object payload, Exception causeOfError);

    /**
     * Schedule the message for redelivery after the specified <code>deliveryDelay</code> (called by the {@link DurableQueueConsumer})
     *
     * @param queueEntryId  the unique id of the message that must we will retry the delivery of
     * @param causeForRetry the reason why the message delivery has to be retried
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return
     */
    boolean retryMessage(QueueEntryId queueEntryId,
                         Exception causeForRetry,
                         Duration deliveryDelay);

    /**
     * Mark a Message as a Dead Letter Message.  Dead Letter Messages won't be delivered to any {@link DurableQueueConsumer} (called by the {@link DurableQueueConsumer})<br>
     * To deliver a Dead Letter Message you must first resurrect the message using {@link #resurrectDeadLetterMessage(QueueEntryId, Duration)}
     *
     * @param queueEntryId                    the unique id of the message that must be marked as a Dead Letter Message
     * @param causeForBeingMarkedAsDeadLetter the reason for the message being marked as a Dead Letter Message
     * @return true if the operation went well, otherwise false
     */
    boolean markAsDeadLetterMessage(QueueEntryId queueEntryId,
                                    Exception causeForBeingMarkedAsDeadLetter);

    /**
     * Resurrect a Dead Letter Message for redelivery after the specified <code>deliveryDelay</code>
     *
     * @param queueEntryId  the unique id of the Dead Letter Message that must we will retry the delivery of
     * @param deliveryDelay how long will the queue wait until it delivers the message to the {@link DurableQueueConsumer}
     * @return true if the operation went well, otherwise false
     */
    boolean resurrectDeadLetterMessage(QueueEntryId queueEntryId,
                                       Duration deliveryDelay);

    /**
     * Delete a message (Queued or Dead Letter Message)
     *
     * @param queueEntryId the unique id of the Message to delete
     * @return true if the operation went well, otherwise false
     */
    boolean deleteMessage(QueueEntryId queueEntryId);

    /**
     * Query the next Queued Message (i.e. not including Dead Letter Messages) that's ready to be delivered to a {@link DurableQueueConsumer}
     *
     * @param queueName the name of the Queue where we will query for the next message ready for delivery
     * @return the message wrapped in an {@link Optional} or {@link Optional#empty()} if no message is ready for delivery
     */
    Optional<QueuedMessage> getNextMessageReadyForDelivery(QueueName queueName);

    /**
     * Check if there are any messages queued  (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for queued messages
     * @return true if there are messages queued on the given queue, otherwise false
     */
    boolean hasMessagesQueuedFor(QueueName queueName);

    /**
     * Get the total number of messages queued (i.e. not including Dead Letter Messages) for the given queue
     *
     * @param queueName the name of the Queue where we will query for queued messages
     * @return the number of queued messages
     */
    long getTotalMessagesQueuedFor(QueueName queueName);

    /**
     * Query Queued Messages (i.e. not including Dead Letter Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#id}
     * @param startIndex        the index of the first message to include (used for pagination)
     * @param pageSize          how many messages to include (used for pagination)
     * @return the messages matching the criteria
     */
    List<QueuedMessage> getQueuedMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize);

    /**
     * Query Dead Letter Messages (i.e. not normally Queued Messages) for the given Queue
     *
     * @param queueName         the name of the Queue where we will query for queued messages
     * @param queueingSortOrder the sort order for the {@link QueuedMessage#id}
     * @param startIndex        the index of the first message to include (used for pagination)
     * @param pageSize          how many messages to include (used for pagination)
     * @return the messages matching the criteria
     */
    List<QueuedMessage> getDeadLetterMessages(QueueName queueName, QueueingSortOrder queueingSortOrder, long startIndex, long pageSize);

    /**
     * Delete all messages (Queued or Dead letter Messages) in the given queue
     *
     * @param queueName the name of the Queue for which all messages will be deleted
     * @return the number of deleted messages
     */
    int purgeQueue(QueueName queueName);
}
