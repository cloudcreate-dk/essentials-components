package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;

public class NoActiveUnitOfWorkException extends EventStoreException {
    public NoActiveUnitOfWorkException() {
    }

    public NoActiveUnitOfWorkException(String message) {
        super(message);
    }

    public NoActiveUnitOfWorkException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoActiveUnitOfWorkException(Throwable cause) {
        super(cause);
    }

    public NoActiveUnitOfWorkException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
