package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

/**
 * Represents an Exception that occurred in the {@link EventStore}
 */
public class EventStoreException extends RuntimeException {
    public EventStoreException() {
    }

    public EventStoreException(String message) {
        super(message);
    }

    public EventStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventStoreException(Throwable cause) {
        super(cause);
    }

    public EventStoreException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
