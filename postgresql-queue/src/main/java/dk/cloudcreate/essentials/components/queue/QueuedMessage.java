package dk.cloudcreate.essentials.components.queue;

import java.time.OffsetDateTime;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Represents a message queued onto a Durable Queue
 */
public class QueuedMessage {
    public final QueueEntryId   id;
    public final QueueName      queueName;
    public final Object         payload;
    public final OffsetDateTime addedTimestamp;
    public final OffsetDateTime nextDeliveryTimestamp;
    public final String         lastDeliveryError;
    public final int            totalDeliveryAttempts;
    public final int            redeliveryAttempts;
    public final boolean        isDeadLetterMessage;

    public QueuedMessage(QueueEntryId id,
                         QueueName queueName,
                         Object payload,
                         OffsetDateTime addedTimestamp,
                         OffsetDateTime nextDeliveryTimestamp,
                         String lastDeliveryError,
                         int totalDeliveryAttempts,
                         int redeliveryAttempts,
                         boolean isDeadLetterMessage) {
        this.id = requireNonNull(id, "No queue entry id provided");
        this.queueName = requireNonNull(queueName, "No queueName provided");
        this.payload = requireNonNull(payload, "No payload provided");
        this.addedTimestamp = requireNonNull(addedTimestamp, "No addedTimestamp provided");
        this.nextDeliveryTimestamp = nextDeliveryTimestamp;
        this.lastDeliveryError = lastDeliveryError;
        this.totalDeliveryAttempts = totalDeliveryAttempts;
        this.redeliveryAttempts = redeliveryAttempts;
        this.isDeadLetterMessage = isDeadLetterMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueuedMessage)) return false;
        QueuedMessage that = (QueuedMessage) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "QueuedMessage{" +
                "id=" + id +
                ", queueName=" + queueName +
                ", payload-type=" + payload.getClass().getName() +
                ", addedTimestamp=" + addedTimestamp +
                ", nextDeliveryTimestamp=" + nextDeliveryTimestamp +
                ", totalDeliveryAttempts=" + totalDeliveryAttempts +
                ", redeliveryAttempts=" + redeliveryAttempts +
                ", isDeadLetterMessage=" + isDeadLetterMessage +
                ", lastDeliveryError='" + lastDeliveryError + '\'' +
                '}';
    }
}
