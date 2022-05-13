package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;

public class JSONDeserializationException extends EventStoreException {
    public JSONDeserializationException(String message) {
        super(message);
    }

    public JSONDeserializationException(String msg, Exception cause) {
        super(msg, cause);
    }
}

