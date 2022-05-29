package dk.cloudcreate.essentials.components.eventsourced.aggregates;

public class AggregateException extends RuntimeException {
    public AggregateException() {
    }

    public AggregateException(String message) {
        super(message);
    }

    public AggregateException(String message, Throwable cause) {
        super(message, cause);
    }

    public AggregateException(Throwable cause) {
        super(cause);
    }

    public AggregateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
