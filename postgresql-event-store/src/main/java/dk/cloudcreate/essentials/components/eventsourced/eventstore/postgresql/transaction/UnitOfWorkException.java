package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;

/**
 * Represents an Exception that occurred in the {@link EventStore} in relation to a {@link UnitOfWork}
 */
public class UnitOfWorkException extends EventStoreException {
    public UnitOfWorkException() {
    }

    public UnitOfWorkException(String message) {
        super(message);
    }

    public UnitOfWorkException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnitOfWorkException(Throwable cause) {
        super(cause);
    }

    public UnitOfWorkException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
