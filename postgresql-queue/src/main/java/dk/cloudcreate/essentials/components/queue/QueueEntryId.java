package dk.cloudcreate.essentials.components.queue;

import dk.cloudcreate.essentials.types.LongType;

/**
 * The unique entry for a message in a queue. This id is unique across all messages across all Queues (as identified by the {@link QueueName})
 */
public class QueueEntryId extends LongType<QueueEntryId> {
    public QueueEntryId(Long value) {
        super(value);
    }

    public static QueueEntryId of(long value) {
        return new QueueEntryId(value);
    }
}