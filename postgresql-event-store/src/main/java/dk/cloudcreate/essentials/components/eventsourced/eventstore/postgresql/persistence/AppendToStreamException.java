package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;

public class AppendToStreamException extends EventStoreException {
    public AppendToStreamException(String msg, RuntimeException cause) {
        super(msg, cause);
    }
}
