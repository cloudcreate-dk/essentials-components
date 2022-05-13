package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

public class AggregateRootException extends RuntimeException {
    public AggregateRootException() {
    }

    public AggregateRootException(String message) {
        super(message);
    }

    public AggregateRootException(String message, Throwable cause) {
        super(message, cause);
    }

    public AggregateRootException(Throwable cause) {
        super(cause);
    }

    public AggregateRootException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
