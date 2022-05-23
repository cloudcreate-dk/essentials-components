package dk.cloudcreate.essentials.components.queue;


import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class DurableQueueException extends RuntimeException {
    public final QueueName              queueName;
    public final Optional<QueueEntryId> queueEntryId;


    public DurableQueueException(String message, QueueName queueName) {
        this(message, null, queueName, null);
    }

    public DurableQueueException(String message, Throwable cause, QueueName queueName) {
        this(message, cause, queueName, null);
    }

    public DurableQueueException(Throwable cause, QueueName queueName) {
        this(null, cause, queueName, null);
    }

    public DurableQueueException(String message, QueueName queueName, QueueEntryId queueEntryId) {
        this(message, null, queueName, queueEntryId);
    }

    public DurableQueueException(Throwable cause, QueueName queueName, QueueEntryId queueEntryId) {
        this(null, cause, queueName, queueEntryId);
    }

    public DurableQueueException(String message, Throwable cause, QueueName queueName, QueueEntryId queueEntryId) {
        super(enrichMessage(message, queueName, queueEntryId), cause);
        this.queueName = queueName;
        this.queueEntryId = Optional.of(queueEntryId);
    }


    private static String enrichMessage(String message, QueueName queueName, QueueEntryId queueEntryId) {
        requireNonNull(queueEntryId, "queueEntryId option is missing");
        String messageToLog = message != null ? " " + message : "";

        if (queueEntryId != null) {
            return msg("[{}:{}]{}",
                       requireNonNull(queueName, "queueName missing"),
                       queueEntryId,
                       messageToLog);
        } else {
            return msg("[{}]{}",
                       requireNonNull(queueName, "queueName missing"),
                       messageToLog);
        }
    }
}
